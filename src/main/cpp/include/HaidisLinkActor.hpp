/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * ERSAP actor that receives ARRAY_DOUBLE output from HaidisActor,
 * creates a 16-bit header from input size, bit-packs it into a double,
 * and prepends it to the output array.
 *
 * @author gurjyan
 * @project ersap-actor
 */

#ifndef HAIDIS_LINK_ACTOR_HPP
#define HAIDIS_LINK_ACTOR_HPP

#include <ersap/engine.hpp>
#include <ersap/engine_data.hpp>
#include <ersap/engine_data_type.hpp>
#include <string>
#include <memory>
#include <set>
#include <vector>
#include <cstdint>

namespace ersap {
namespace coda {

/**
 * ERSAP actor for processing HaidisActor output with header prepending.
 *
 * Input:
 *   - ARRAY_DOUBLE: triplets of doubles (s_pippim, s_pippi0, s_pimpi0, ...)
 *     from HaidisActor kinematic analysis
 *
 * Processing:
 *   1. Read input array and compute size metrics
 *   2. Print received data (triplets formatted nicely)
 *   3. Create 16-bit header from input size in bytes (modulo 65536)
 *   4. Bit-pack header into double slot using memcpy (NOT numeric cast)
 *   5. Prepend header-double to output array
 *
 * Output:
 *   - ARRAY_DOUBLE: [header_double, original_data...]
 *     where header_double encodes size_bytes in low 16 bits
 *
 * Header Encoding:
 *   - uint16_t header_u16 = size_bytes & 0xFFFF
 *   - Packed into uint64_t with upper 48 bits zero
 *   - Binary-copied to double via memcpy (strict aliasing safe)
 *
 * Header Decoding (for downstream consumers):
 *   uint64_t u64;
 *   std::memcpy(&u64, &header_double, sizeof(double));
 *   uint16_t size_mod = static_cast<uint16_t>(u64 & 0xFFFF);
 */
class HaidisLinkActor : public ersap::Engine {
public:
    HaidisLinkActor() = default;
    virtual ~HaidisLinkActor() = default;

    // Engine interface implementation
    ersap::EngineData configure(ersap::EngineData& input) override;
    ersap::EngineData execute(ersap::EngineData& input) override;
    ersap::EngineData execute_group(const std::vector<ersap::EngineData>&) override;

    // Data type declarations
    std::vector<ersap::EngineDataType> input_data_types() const override;
    std::vector<ersap::EngineDataType> output_data_types() const override;
    std::set<std::string> states() const override;

    // Engine metadata
    std::string name() const override;
    std::string author() const override;
    std::string description() const override;
    std::string version() const override;

private:
    // Configuration parameters
    bool verbose_ = false;

    // Statistics
    std::size_t eventCount_ = 0;

    // Helper methods
    void printReceivedData(const std::vector<double>& data) const;
};

} // namespace coda
} // namespace ersap

// C interface for engine creation (required by ERSAP framework)
extern "C" std::unique_ptr<ersap::Engine> create_engine();

#endif // HAIDIS_LINK_ACTOR_HPP
