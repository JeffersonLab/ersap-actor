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
#include <cerrno>
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
            if (!config["batch_size"].is_null()) {
                batch_size_ = config["batch_size"].int_value();
                if (batch_size_ <= 0) {
                    output.set_status(ersap::EngineStatus::ERROR);
                    output.set_description("batch_size must be positive, got " + std::to_string(batch_size_));
                    std::cerr << "HaidisGluexLinkActor: batch_size must be positive" << std::endl;
                    return output;
                }
            }
            if (!config["data_id_number"].is_null()) {
                data_id_number_ = config["data_id_number"].int_value();
                if (data_id_number_ <= 0) {
                    output.set_status(ersap::EngineStatus::ERROR);
                    output.set_description("data_id_number must be positive, got " + std::to_string(data_id_number_));
                    std::cerr << "HaidisGluexLinkActor: data_id_number must be positive" << std::endl;
                    return output;
                }
            }

            if (verbose_) {
                std::cout << "HaidisGluexLinkActor configuration:" << std::endl;
                std::cout << "  - verbose:            " << verbose_            << std::endl;
                std::cout << "  - enable_shmem_write: " << enable_shmem_write_ << std::endl;
                std::cout << "  - shmem_name:         " << shmem_name_         << std::endl;
                std::cout << "  - sem_name:           " << sem_name_           << std::endl;
                std::cout << "  - sem_ack_name:       " << sem_ack_name_       << std::endl;
                std::cout << "  - shmem_size:         " << shmem_size_         << std::endl;
                std::cout << "  - batch_size:         " << batch_size_         << std::endl;
                std::cout << "  - data_id_number:     " << data_id_number_     << std::endl;
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

    // Initialize per-data-ID batch buffers (reset on every configure call)
    data_buffers_.assign(data_id_number_, std::vector<double>{});
    event_counts_.assign(data_id_number_, 0);

    if (verbose_) {
        std::cout << "HaidisGluexLinkActor configured successfully" << std::endl;
    }

    return output;
}

ersap::EngineData HaidisGluexLinkActor::execute(ersap::EngineData& input) {

    auto output = ersap::EngineData{};

    // Gate 1: shmem writing disabled — return empty immediately
    if (!enable_shmem_write_) {
        if (verbose_) {
            std::cout << "Shared memory write disabled (enable_shmem_write=false)" << std::endl;
        }
        std::vector<double> empty;
        output.set_data(ersap::type::ARRAY_DOUBLE, empty);
        return output;
    }

    // Gate 2: writer not ready (initialize() failed or not yet called) — return empty immediately
    if (!writer_) {
        std::cerr << "HaidisGluexLinkActor: shared memory writer not ready; "
                     "skipping event " << eventCount_ << std::endl;
        std::vector<double> empty;
        output.set_data(ersap::type::ARRAY_DOUBLE, empty);
        return output;
    }

    // Increment execute call counter
    executeCallCount_++;

    // Debug: Log execute() call information
    if (verbose_) {
        std::cout << "\n========================================" << std::endl;
        std::cout << "DEBUG: HaidisGluexLinkActor::execute() called - Call #" << executeCallCount_ << std::endl;
        std::cout << "  Input MIME type: " << input.mime_type() << std::endl;
        std::cout << "  Writer initialized: YES" << std::endl;
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

        // Compute size metrics
        const std::size_t num_doubles    = data.size();
        const std::uint32_t duplet_count = static_cast<std::uint32_t>(num_doubles / 2);
        const std::size_t complete_elements = duplet_count * 2;

        if (num_doubles % 2 != 0 && verbose_) {
            std::cout << "Warning: Input has " << num_doubles
                      << " doubles (not divisible by 2). "
                      << "Complete duplets: " << duplet_count
                      << ", leftover: " << (num_doubles % 2) << std::endl;
        }

        // Validate data_id against configured range (data IDs are zero-based)
        if (static_cast<int>(data_id) >= data_id_number_) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("data_id " + std::to_string(data_id) +
                                   " out of range [0, " + std::to_string(data_id_number_) + ")");
            std::cerr << "HaidisGluexLinkActor: data_id " << data_id
                      << " >= data_id_number " << data_id_number_
                      << ", dropping event " << eventCount_ << std::endl;
            return output;
        }

        // Accumulate complete duplets into the per-data-ID buffer
        auto& buffer = data_buffers_[data_id];
        buffer.insert(buffer.end(), data.begin(), data.begin() + complete_elements);
        event_counts_[data_id]++;

        if (verbose_) {
            std::cout << "  Batch state: data_id=" << data_id
                      << " events=" << event_counts_[data_id]
                      << "/" << batch_size_
                      << " buffered_doubles=" << buffer.size() << std::endl;
        }

        // Write to shared memory when the batch for this data_id is complete
        if (event_counts_[data_id] >= batch_size_) {
            const std::uint32_t batch_duplet_count = static_cast<std::uint32_t>(buffer.size() / 2);
            const std::vector<uint32_t> dims = {batch_duplet_count, 2};

            // Guard: accumulated batch must fit within the configured shared memory region
            const std::size_t payload_bytes = buffer.size() * sizeof(double);
            if (payload_bytes > shmem_size_) {
                std::cerr << "HaidisGluexLinkActor: batch payload (" << payload_bytes
                          << " bytes) exceeds shmem_size (" << shmem_size_
                          << " bytes), dropping batch for data_id " << data_id << std::endl;
                buffer.clear();
                event_counts_[data_id] = 0;
            } else if (!writer_->write_data(buffer, 2, dims, data_id)) {
                const int saved_errno = errno;
                buffer.clear();
                event_counts_[data_id] = 0;
                if (saved_errno == ETIMEDOUT) {
                    std::cerr << "HaidisGluexLinkActor: write_data timed out after "
                              << ShmemWriter::WRITE_TIMEOUT_SEC << "s, dropping batch for data_id "
                              << data_id << " (event " << eventCount_ << ")" << std::endl;
                    std::vector<double> empty;
                    output.set_data(ersap::type::ARRAY_DOUBLE, empty);
                    eventCount_++;
                    return output;
                }
                writeFailureCount_++;
                consecutiveFailures_++;
                std::cerr << "HaidisGluexLinkActor: write_data failed for data_id " << data_id
                          << " (event " << eventCount_ << ", consecutive failures: "
                          << consecutiveFailures_ << ")" << std::endl;
                if (consecutiveFailures_ >= 3) {
                    output.set_status(ersap::EngineStatus::WARNING);
                    output.set_description("Shared memory write failing persistently (failures: " +
                                         std::to_string(consecutiveFailures_) + ")");
                }
            } else {
                consecutiveFailures_ = 0;
                if (verbose_) {
                    std::cout << "  BATCH FLUSHED: data_id=" << data_id
                              << " doubles_written=" << buffer.size()
                              << " duplets=" << batch_duplet_count
                              << " (batch_size=" << batch_size_ << ")" << std::endl;
                }
                buffer.clear();
                event_counts_[data_id] = 0;
            }
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

        // Pass current event's data through to downstream actors (data_id stripped)
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
    return {}; // No externally-named states (batch buffers are internal actor state)
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
