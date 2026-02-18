/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * Implementation of HaidisLinkActor for header prepending to ARRAY_DOUBLE
 * @author gurjyan
 * @project ersap-actor
 */

#include "HaidisLinkActor.hpp"
#include <ersap/stdlib/json_utils.hpp>
#include <iostream>
#include <iomanip>
#include <cstring>
#include <cstdint>

// C interface for engine creation
extern "C"
std::unique_ptr<ersap::Engine> create_engine()
{
    return std::make_unique<ersap::coda::HaidisLinkActor>();
}

namespace ersap {
namespace coda {

ersap::EngineData HaidisLinkActor::configure(ersap::EngineData& input) {
    auto output = ersap::EngineData{};

    // Parse JSON configuration if provided
    if (input.mime_type() == ersap::type::JSON.mime_type()) {
        try {
            auto config = ersap::stdlib::parse_json(input);

            if (!config["verbose"].is_null()) {
                verbose_ = config["verbose"].bool_value();
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
                std::cout << "HaidisLinkActor configuration:" << std::endl;
                std::cout << "  - verbose:      " << verbose_      << std::endl;
                std::cout << "  - shmem_name:   " << shmem_name_   << std::endl;
                std::cout << "  - sem_name:     " << sem_name_     << std::endl;
                std::cout << "  - sem_ack_name: " << sem_ack_name_ << std::endl;
                std::cout << "  - shmem_size:   " << shmem_size_   << std::endl;
            }

        } catch (const std::exception& e) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Error parsing configuration: " + std::string(e.what()));
            std::cerr << "Error parsing HaidisLinkActor configuration: " << e.what() << std::endl;
            return output;
        }
    }

    // (Re-)initialize shared memory writer with current config.
    // Reset first so the destructor releases any existing IPC resources.
    writer_.reset();
    writer_ = std::make_unique<ShmemWriter>(shmem_name_, shmem_size_, sem_name_, sem_ack_name_);
    if (!writer_->initialize()) {
        writer_.reset();
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Failed to initialize shared memory writer for " + shmem_name_);
        std::cerr << "HaidisLinkActor: Failed to initialize shared memory '" << shmem_name_ << "'" << std::endl;
        return output;
    }

    if (verbose_) {
        std::cout << "HaidisLinkActor configured successfully" << std::endl;
    }

    return output;
}

ersap::EngineData HaidisLinkActor::execute(ersap::EngineData& input) {
    auto output = ersap::EngineData{};

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

        // Step 1: Compute size metrics
        const std::size_t num_doubles   = in.size();
        const std::size_t size_bytes    = num_doubles * sizeof(double);
        const std::uint32_t triplet_count = static_cast<std::uint32_t>(num_doubles / 3);

        if (num_doubles % 3 != 0 && verbose_) {
            std::cout << "Warning: Input has " << num_doubles
                      << " doubles (not divisible by 3). "
                      << "Complete triplets: " << triplet_count
                      << ", leftover: " << (num_doubles % 3) << std::endl;
        }

        // Send received data to shared memory as a 2-D array (triplet_count × 3)
        if (writer_) {
            const std::vector<uint32_t> dims = {triplet_count, 3};
            if (!writer_->write_data(in, 2, dims)) {
                std::cerr << "HaidisLinkActor: write_data failed (event "
                          << eventCount_ << ")" << std::endl;
                // Non-fatal: log and continue — do not drop the event
            }
        } else {
            std::cerr << "HaidisLinkActor: shared memory writer not initialized; "
                         "skipping write (event " << eventCount_ << ")" << std::endl;
        }

        // Print received data if verbose
        if (verbose_) {
            printReceivedData(in);
        }

        // Update statistics
        eventCount_++;

        if (verbose_ && eventCount_ % 100 == 0) {
            std::cout << "\nHaidisLinkActor Statistics:" << std::endl;
            std::cout << "  Events processed: " << eventCount_ << std::endl;
        }

        // Pass input through to downstream ERSAP actors unchanged
        output.set_data(ersap::type::ARRAY_DOUBLE, in);

    } catch (const std::exception& e) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Error processing input: " + std::string(e.what()));
        std::cerr << "Error in HaidisLinkActor: " << e.what() << std::endl;
    }

    return output;
}

ersap::EngineData HaidisLinkActor::execute_group(const std::vector<ersap::EngineData>&) {
    // Group processing not implemented for this actor
    return {};
}

std::vector<ersap::EngineDataType> HaidisLinkActor::input_data_types() const {
    return { ersap::type::ARRAY_DOUBLE, ersap::type::JSON };
}

std::vector<ersap::EngineDataType> HaidisLinkActor::output_data_types() const {
    return { ersap::type::ARRAY_DOUBLE };
}

std::set<std::string> HaidisLinkActor::states() const {
    return {}; // Stateless engine
}

std::string HaidisLinkActor::name() const {
    return "HaidisLinkActor";
}

std::string HaidisLinkActor::author() const {
    return "Jefferson Lab";
}

std::string HaidisLinkActor::description() const {
    return "ERSAP actor that receives ARRAY_DOUBLE from HaidisActor (triplets: s_pippim, "
           "s_pippi0, s_pimpi0), creates a 64-bit header (32+16+16 bit structure) encoding "
           "size_bytes, triplet_count, and num_doubles, copies it into a double using memcpy, "
           "and prepends it to the output array.";
}

std::string HaidisLinkActor::version() const {
    return "1.0.0";
}

void HaidisLinkActor::printReceivedData(const std::vector<double>& data) const {
    size_t num_doubles = data.size();
    size_t size_bytes = num_doubles * sizeof(double);
    size_t triplet_count = num_doubles / 3;

    std::cout << "\n========================================" << std::endl;
    std::cout << "HaidisLinkActor: Received Data" << std::endl;
    std::cout << "========================================" << std::endl;
    std::cout << "Received " << num_doubles << " doubles (" << size_bytes << " bytes)" << std::endl;
    std::cout << "Number of triplets: " << triplet_count << std::endl;

    if (num_doubles == 0) {
        std::cout << "(Empty input)" << std::endl;
    } else {
        std::cout << "\nTriplet data (s_pippim, s_pippi0, s_pimpi0):" << std::endl;

        // Print triplets (3 doubles per row)
        for (size_t i = 0; i < triplet_count; ++i) {
            size_t idx = i * 3;
            std::cout << "  Triplet " << (i + 1) << ": "
                      << "s_pippim=" << std::fixed << std::setprecision(6) << data[idx] << ", "
                      << "s_pippi0=" << data[idx + 1] << ", "
                      << "s_pimpi0=" << data[idx + 2] << std::endl;
        }

        // Print any leftover values
        size_t leftover = num_doubles % 3;
        if (leftover > 0) {
            size_t idx = triplet_count * 3;
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
