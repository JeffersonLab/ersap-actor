/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * Implementation of HaidisGluexLinkActor for header prepending to ARRAY_DOUBLE
 * @author gurjyan
 * @project ersap-actor
 */

#include "HaidisGluexLinkActor.hpp"
#include <ersap/stdlib/json_utils.hpp>
#include <iostream>
#include <iomanip>
#include <cstring>
#include <cstdint>

// C interface for engine creation
extern "C"
std::unique_ptr<ersap::Engine> create_engine()
{
    return std::make_unique<ersap::coda::HaidisGluexLinkActor>();
}

namespace ersap {
namespace coda {

ersap::EngineData HaidisGluexLinkActor::configure(ersap::EngineData& input) {
    auto output = ersap::EngineData{};

    std::cout << "\nDEBUG: HaidisGluexLinkActor::configure() called" << std::endl;
    std::cout << "  Input MIME type: " << input.mime_type() << std::endl;

    // Parse JSON configuration if provided
    if (input.mime_type() == ersap::type::JSON.mime_type()) {
        try {
            auto config = ersap::stdlib::parse_json(input);

            if (!config["verbose"].is_null()) {
                verbose_ = config["verbose"].bool_value();
            }
            if (!config["enable_shmem_write"].is_null()) {
                enable_shmem_write_ = config["enable_shmem_write"].bool_value();
            }
            if (!config["shmem_name"].is_null()) {
                shmem_name_ = config["shmem_name"].string_value();
            }
            if (!config["sem_name"].is_null()) {
                sem_name_ = config["sem_name"].string_value();
            }
            if (!config["shmem_size"].is_null()) {
                shmem_size_ = static_cast<std::size_t>(config["shmem_size"].int_value());
            }
            if (!config["sem_ack_name"].is_null()) {
                sem_ack_name_ = config["sem_ack_name"].string_value();
            }

            if (verbose_) {
                std::cout << "HaidisGluexLinkActor configuration:" << std::endl;
                std::cout << "  - verbose:            " << verbose_            << std::endl;
                std::cout << "  - enable_shmem_write: " << enable_shmem_write_ << std::endl;
                std::cout << "  - shmem_name:         " << shmem_name_         << std::endl;
                std::cout << "  - sem_name:           " << sem_name_           << std::endl;
                std::cout << "  - sem_ack_name:       " << sem_ack_name_       << std::endl;
                std::cout << "  - shmem_size:         " << shmem_size_         << std::endl;
            }

        } catch (const std::exception& e) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Error parsing configuration: " + std::string(e.what()));
            std::cerr << "Error parsing HaidisGluexLinkActor configuration: " << e.what() << std::endl;
            return output;
        }
    }

    if (enable_shmem_write_) {
        writer_ = std::make_unique<ShmemWriter>(shmem_name_, shmem_size_, sem_name_, sem_ack_name_);
        if (!writer_->initialize()) {
            writer_.reset();
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Failed to initialize shared memory writer for " + shmem_name_);
            std::cerr << "HaidisGluexLinkActor: Failed to initialize shared memory '" << shmem_name_ << "'" << std::endl;
            return output;
        }
    } else {
        writer_.reset();
    }

    if (verbose_) {
        std::cout << "HaidisGluexLinkActor configured successfully" << std::endl;
    }

    return output;
}

ersap::EngineData HaidisGluexLinkActor::execute(ersap::EngineData& input) {

    auto output = ersap::EngineData{};
    if (!enable_shmem_write_) {
        if (verbose_) {
        std::cout << "Shared memory write disabled (enable_shmem_write=false)" << std::endl;
        }
        return output;
        }


    // Increment execute call counter
    executeCallCount_++;

    // Debug: Log execute() call information
    if (verbose_) {
        std::cout << "\n========================================" << std::endl;
        std::cout << "DEBUG: HaidisGluexLinkActor::execute() called - Call #" << executeCallCount_ << std::endl;
        std::cout << "  Input MIME type: " << input.mime_type() << std::endl;
        std::cout << "  Writer initialized: " << (writer_ ? "YES" : "NO") << std::endl;
        std::cout << "========================================\n" << std::endl;
    }

    // Verify input data type - expecting ARRAY_DOUBLE
    if (input.mime_type() != ersap::type::ARRAY_DOUBLE.mime_type()) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Wrong input type: expected ARRAY_DOUBLE, got " + input.mime_type());
        std::cerr << "Error: Expected ARRAY_DOUBLE input type, got " << input.mime_type() << std::endl;
        return output;
    }

    try {
        // Extract input data as vector of doubles
        auto& in = ersap::data_cast<std::vector<double>>(input);

        // Guard against empty input before reading in[0]
        if (in.empty()) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Empty input: expected at least one double (data_id)");
            std::cerr << "Error: HaidisGluexLinkActor received empty input" << std::endl;
            return output;
        }

        // Extract data_id from the first element, then copy the remainder into data
        const std::uint16_t data_id = static_cast<std::uint16_t>(in[0]);
        std::vector<double> data(in.begin() + 1, in.end());

        // Step 1: Compute size metrics
        const std::size_t num_doubles   = data.size();
        const std::size_t size_bytes    = num_doubles * sizeof(double);
        const std::uint32_t duplet_count = static_cast<std::uint32_t>(num_doubles / 2);

        if (num_doubles % 2 != 0 && verbose_) {
            std::cout << "Warning: Input has " << num_doubles
                      << " doubles (not divisible by 2). "
                      << "Complete duplets: " << duplet_count
                      << ", leftover: " << (num_doubles % 2) << std::endl;
        }

        // Write to shared memory requires reader process)
            if (writer_) {
                const std::vector<uint32_t> dims = {duplet_count, 2};
                const std::size_t complete_elements = duplet_count * 2;

                // Create vector with only complete duplets
                std::vector<double> complete_data(data.begin(), data.begin() + complete_elements);

                if (!writer_->write_data(complete_data, 2, dims, data_id)) {
                    writeFailureCount_++;
                    consecutiveFailures_++;
                    std::cerr << "HaidisGluexLinkActor: write_data failed (event "
                              << eventCount_ << ", consecutive failures: "
                              << consecutiveFailures_ << ")" << std::endl;

                    // Set warning status if failures are persistent
                    if (consecutiveFailures_ >= 3) {
                        output.set_status(ersap::EngineStatus::WARNING);
                        output.set_description("Shared memory write failing persistently (failures: " +
                                             std::to_string(consecutiveFailures_) + ")");
                    }
                } else {
                    // Reset consecutive failure counter on success
                    consecutiveFailures_ = 0;
                }
            } else {
                std::cerr << "HaidisGluexLinkActor: shared memory writer not initialized; "
                             "skipping write (event " << eventCount_ << ")" << std::endl;
                output.set_status(ersap::EngineStatus::WARNING);
                output.set_description("Shared memory writer not initialized");
            }

        // Print received data if verbose
        if (verbose_) {
            printReceivedData(data);
        }

        // Update statistics
        eventCount_++;

        if (verbose_ && eventCount_ % 100 == 0) {
            std::cout << "\nHaidisGluexLinkActor Statistics:" << std::endl;
            std::cout << "  Events processed: " << eventCount_ << std::endl;
            std::cout << "  Write failures:   " << writeFailureCount_ << std::endl;
            std::cout << "  Consecutive failures: " << consecutiveFailures_ << std::endl;
        }

        // Pass analysis results through to downstream ERSAP actors (data_id stripped)
        output.set_data(ersap::type::ARRAY_DOUBLE, data);

        // Debug: Log output summary
        if (verbose_) {
            std::cout << "\nDEBUG: HaidisGluexLinkActor::execute() returning - Call #" << executeCallCount_ << std::endl;
            std::cout << "  Received " << num_doubles << " doubles" << std::endl;
            std::cout << "  Duplet count: " << duplet_count << std::endl;
            std::cout << "  Output data type: ARRAY_DOUBLE" << std::endl;
            std::cout << "  Total execute calls so far: " << executeCallCount_ << std::endl;
            std::cout << "  Total events processed: " << eventCount_ << std::endl;
            std::cout << "  Write failures: " << writeFailureCount_ << std::endl;
            std::cout << "========================================\n" << std::endl;
        }

    } catch (const std::exception& e) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Error processing input: " + std::string(e.what()));
        std::cerr << "Error in HaidisGluexLinkActor: " << e.what() << std::endl;
    }

    return output;
}

ersap::EngineData HaidisGluexLinkActor::execute_group(const std::vector<ersap::EngineData>&) {
    // Group processing not implemented for this actor
    return {};
}

std::vector<ersap::EngineDataType> HaidisGluexLinkActor::input_data_types() const {
    return { ersap::type::ARRAY_DOUBLE, ersap::type::JSON };
}

std::vector<ersap::EngineDataType> HaidisGluexLinkActor::output_data_types() const {
    return { ersap::type::ARRAY_DOUBLE };
}

std::set<std::string> HaidisGluexLinkActor::states() const {
    return {}; // Stateless engine
}

std::string HaidisGluexLinkActor::name() const {
    return "HaidisGluexLinkActor";
}

std::string HaidisGluexLinkActor::author() const {
    return "Jefferson Lab";
}

std::string HaidisGluexLinkActor::description() const {
    return "ERSAP actor that receives ARRAY_DOUBLE from HaidisGluexActor (duplets: value1, "
           "value2), writes data to shared memory (if enabled), and passes the data "
           "through to downstream actors unchanged.";
}

std::string HaidisGluexLinkActor::version() const {
    return "1.0.0";
}

void HaidisGluexLinkActor::printReceivedData(const std::vector<double>& data) const {
    size_t num_doubles = data.size();
    size_t size_bytes = num_doubles * sizeof(double);
    size_t duplet_count = num_doubles / 2;

    std::cout << "\n========================================" << std::endl;
    std::cout << "HaidisGluexLinkActor: Received Data" << std::endl;
    std::cout << "========================================" << std::endl;
    std::cout << "Received " << num_doubles << " doubles (" << size_bytes << " bytes)" << std::endl;
    std::cout << "Number of duplets: " << duplet_count << std::endl;

    if (num_doubles == 0) {
        std::cout << "(Empty input)" << std::endl;
    } else {
        std::cout << "\nDuplet data (value1, value2):" << std::endl;

        // Print duplets (2 doubles per row)
        for (size_t i = 0; i < duplet_count; ++i) {
            size_t idx = i * 2;
            std::cout << "  Duplet " << (i + 1) << ": "
                      << "value1=" << std::fixed << std::setprecision(6) << data[idx] << ", "
                      << "value2=" << data[idx + 1] << std::endl;
        }

        // Print any leftover values
        size_t leftover = num_doubles % 2;
        if (leftover > 0) {
            size_t idx = duplet_count * 2;
            std::cout << "  Leftover values: ";
            for (size_t i = 0; i < leftover; ++i) {
                std::cout << data[idx + i];
                if (i < leftover - 1) std::cout << ", ";
            }
            std::cout << std::endl;
        }
    }

    std::cout << "========================================" << std::endl;
}
} // namespace coda
} // namespace ersap
