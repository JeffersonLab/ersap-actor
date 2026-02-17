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

            if (verbose_) {
                std::cout << "HaidisLinkActor configuration:" << std::endl;
                std::cout << "  - verbose:    " << verbose_    << std::endl;
                std::cout << "  - shmem_name: " << shmem_name_ << std::endl;
                std::cout << "  - sem_name:   " << sem_name_   << std::endl;
                std::cout << "  - shmem_size: " << shmem_size_ << std::endl;
            }

        } catch (const std::exception& e) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Error parsing configuration: " + std::string(e.what()));
            std::cerr << "Error parsing HaidisLinkActor configuration: " << e.what() << std::endl;
            return output;
        }
    }

    // (Re-)initialize shared memory writer with current config
    writer_ = std::make_unique<ShmemWriter>(shmem_name_, shmem_size_, sem_name_);
    if (!writer_->initialize()) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Failed to initialize ShmemWriter for " + shmem_name_);
        std::cerr << "HaidisLinkActor: failed to initialize ShmemWriter" << std::endl;
        writer_.reset();
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

        // Step 2: Build contiguous [header][payload] buffer.
        //
        // Header layout (20 bytes):
        //   Offset  Size  Field
        //   0        8    size_t  : size_bytes
        //   8        4    uint32_t: constant 2
        //   12       4    uint32_t: triplet_count
        //   16       4    uint32_t: constant 3
        // Payload (size_bytes):
        //   20       N*8  double[]: raw doubles
        //
        const std::size_t HEADER_BYTES = sizeof(std::size_t) + 3 * sizeof(std::uint32_t);
        const std::size_t total_bytes  = HEADER_BYTES + size_bytes;

        if (writer_ && total_bytes > shmem_size_) {
            std::cerr << "HaidisLinkActor: message size " << total_bytes
                      << " bytes exceeds shmem capacity " << shmem_size_ << " bytes — skipping write" << std::endl;
        } else {
            std::vector<std::uint8_t> buf(total_bytes);
            std::size_t off = 0;

            // Header field 1: payload size in bytes
            std::memcpy(buf.data() + off, &size_bytes,    sizeof(std::size_t));   off += sizeof(std::size_t);
            // Header field 2: constant 2
            std::uint32_t a = 2;
            std::memcpy(buf.data() + off, &a,             sizeof(std::uint32_t)); off += sizeof(std::uint32_t);
            // Header field 3: triplet count
            std::memcpy(buf.data() + off, &triplet_count, sizeof(std::uint32_t)); off += sizeof(std::uint32_t);
            // Header field 4: constant 3
            std::uint32_t b = 3;
            std::memcpy(buf.data() + off, &b,             sizeof(std::uint32_t)); off += sizeof(std::uint32_t);
            // Payload: raw double bytes
            std::memcpy(buf.data() + off, in.data(),      size_bytes);

            if (writer_) {
                if (!writer_->write_data(buf.data(), buf.size())) {
                    std::cerr << "HaidisLinkActor: write_data failed for event " << eventCount_ << std::endl;
                }
            } else {
                std::cerr << "HaidisLinkActor: ShmemWriter not initialized — dropping event " << eventCount_ << std::endl;
            }
            if (verbose_) {
            std::cout << "HaidisLinkActor: write_data for event " << eventCount_ << std::endl;
            }
        }

        // Step 3: Print received data (verbose only)
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
