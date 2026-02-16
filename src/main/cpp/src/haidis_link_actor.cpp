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

            // Parse verbose flag
            if (!config["verbose"].is_null()) {
                verbose_ = config["verbose"].bool_value();
            }

            if (verbose_) {
                std::cout << "HaidisLinkActor configuration:" << std::endl;
                std::cout << "  - Verbose: " << verbose_ << std::endl;
            }

        } catch (const std::exception& e) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Error parsing configuration: " + std::string(e.what()));
            std::cerr << "Error parsing HaidisLinkActor configuration: " << e.what() << std::endl;
            return output;
        }
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
        size_t num_doubles = in.size();
        size_t size_bytes = num_doubles * sizeof(double);
        size_t triplet_count = num_doubles / 3;

        // Warn if incomplete triplets
        if (num_doubles % 3 != 0 && verbose_) {
            std::cout << "Warning: Input has " << num_doubles
                      << " doubles (not divisible by 3). "
                      << "Complete triplets: " << triplet_count
                      << ", leftover: " << (num_doubles % 3) << std::endl;
        }

        // Step 2: Print received data
        if (verbose_) {
            printReceivedData(in);
        }

        // Step 3: Create 64-bit header with 32+16+16 bit structure
        // Header layout (64 bits total):
        //   Bits  0-15 (uint16_t): num_doubles & 0xFFFF
        //   Bits 16-31 (uint16_t): triplet_count & 0xFFFF
        //   Bits 32-63 (uint32_t): size_bytes (full 32-bit size)

        uint64_t header_u64 = 0;
        header_u64 |= (static_cast<uint64_t>(size_bytes) << 32);                    // bits 32-63
        header_u64 |= (static_cast<uint64_t>(triplet_count & 0xFFFF) << 16);        // bits 16-31
        header_u64 |= (num_doubles & 0xFFFF);                                       // bits  0-15

        if (verbose_) {
            std::cout << "\nHeader generation (64-bit structure):" << std::endl;
            std::cout << "  - num_doubles: " << num_doubles
                      << " (0x" << std::hex << (num_doubles & 0xFFFF) << std::dec << ")" << std::endl;
            std::cout << "  - triplet_count: " << triplet_count
                      << " (0x" << std::hex << (triplet_count & 0xFFFF) << std::dec << ")" << std::endl;
            std::cout << "  - size_bytes: " << size_bytes
                      << " (0x" << std::hex << size_bytes << std::dec << ")" << std::endl;
            std::cout << "  - Full 64-bit header: 0x" << std::hex << header_u64 << std::dec << std::endl;
        }

        // Step 4: Binary-copy 64-bit header to double using memcpy
        // This is a binary container operation, not numeric conversion
        static_assert(sizeof(double) == sizeof(uint64_t), "double must be 8 bytes");

        double header_double;
        std::memcpy(&header_double, &header_u64, sizeof(header_u64));

        // To decode this header (for downstream consumers):
        // uint64_t u64;
        // std::memcpy(&u64, &header_double, sizeof(double));
        // uint16_t num_doubles_decoded = static_cast<uint16_t>(u64 & 0xFFFF);
        // uint16_t triplet_count_decoded = static_cast<uint16_t>((u64 >> 16) & 0xFFFF);
        // uint32_t size_bytes_decoded = static_cast<uint32_t>(u64 >> 32);

        if (verbose_) {
            std::cout << "  - Binary-copied to double via memcpy" << std::endl;

            // Verification: decode to confirm correct packing
            uint64_t verify_u64;
            std::memcpy(&verify_u64, &header_double, sizeof(double));
            uint16_t verify_num_doubles = static_cast<uint16_t>(verify_u64 & 0xFFFF);
            uint16_t verify_triplet_count = static_cast<uint16_t>((verify_u64 >> 16) & 0xFFFF);
            uint32_t verify_size_bytes = static_cast<uint32_t>(verify_u64 >> 32);

            std::cout << "  - Verification (decoded):" << std::endl;
            std::cout << "    * num_doubles: " << verify_num_doubles << std::endl;
            std::cout << "    * triplet_count: " << verify_triplet_count << std::endl;
            std::cout << "    * size_bytes: " << verify_size_bytes << std::endl;
        }

        // Step 5: Construct output array with header prepended
        std::vector<double> out;
        out.reserve(num_doubles + 1);
        out.push_back(header_double);  // Prepend header as first element
        out.insert(out.end(), in.begin(), in.end());  // Append all input doubles

        if (verbose_) {
            std::cout << "\nOutput array:" << std::endl;
            std::cout << "  - Total elements: " << out.size()
                      << " (1 header + " << num_doubles << " data)" << std::endl;
            std::cout << "  - Output size: " << (out.size() * sizeof(double)) << " bytes" << std::endl;
        }

        // Update statistics
        eventCount_++;

        if (verbose_ && eventCount_ % 100 == 0) {
            std::cout << "\nHaidisLinkActor Statistics:" << std::endl;
            std::cout << "  Events processed: " << eventCount_ << std::endl;
        }

        // Set output data
        output.set_data(ersap::type::ARRAY_DOUBLE, out);

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

// Private helper methods

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
