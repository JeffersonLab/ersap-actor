/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * ERSAP actor that receives ARRAY_DOUBLE output from HaidisActor,
 * constructs a structured binary header, and writes [header][payload]
 * to a POSIX shared memory region via ShmemWriter.
 *
 * @author gurjyan
 * @project ersap-actor
 */

#ifndef HAIDIS_LINK_ACTOR_HPP
#define HAIDIS_LINK_ACTOR_HPP

#include "haidis_shem_writer.hpp"
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
 * ERSAP actor for processing HaidisActor output and forwarding to shared memory.
 *
 * Input:
 *   - ARRAY_DOUBLE: triplets of doubles (s_pippim, s_pippi0, s_pimpi0, ...)
 *     from HaidisActor kinematic analysis
 *
 * Processing:
 *   1. Read input array and compute size metrics.
 *   2. Optionally print received triplets (verbose mode).
 *   3. Build a contiguous byte buffer:
 *        [custom header (20 bytes)][raw double payload]
 *   4. Write the buffer to POSIX shared memory via ShmemWriter.
 *      The writer does NOT inject any additional header word.
 *
 * Shared-memory layout per message:
 *   Offset  Size  Field
 *   0        8    size_t  : payload size in bytes (num_doubles * sizeof(double))
 *   8        4    uint32_t: constant 2
 *   12       4    uint32_t: triplet_count (num_doubles / 3)
 *   16       4    uint32_t: constant 3
 *   20       N*8  double[] : raw payload
 *
 * Decoding (reader side):
 *   size_t  size_bytes    = *reinterpret_cast<size_t*>(ptr + 0);
 *   uint32_t triplet_count = *reinterpret_cast<uint32_t*>(ptr + 12);
 *   double*  payload       = reinterpret_cast<double*>(ptr + 20);
 *
 * Configuration (JSON):
 *   "verbose"    : bool   - enable per-event logging (default false)
 *   "shmem_name" : string - POSIX shmem object name (default "/haidis_shmem")
 *   "sem_name"   : string - POSIX semaphore name    (default "/haidis_sem")
 *   "shmem_size" : int    - shared memory size bytes (default 10485760 = 10 MB)
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
    std::string shmem_name_ = "/haidis_shmem";
    std::string sem_name_   = "/haidis_sem";
    std::size_t shmem_size_ = 10485760; // 10 MB default

    // Shared memory writer — constructed in configure()
    std::unique_ptr<ShmemWriter> writer_;

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
