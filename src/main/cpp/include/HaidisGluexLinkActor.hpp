/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * ERSAP actor that receives ARRAY_DOUBLE output from HaidisGluexActor,
 * constructs a structured binary header, and writes [header][payload]
 * to a POSIX shared memory region via ShmemWriter.
 *
 * @author gurjyan
 * @project ersap-actor
 */

#ifndef HAIDIS_GLUEX_LINK_ACTOR_HPP
#define HAIDIS_GLUEX_LINK_ACTOR_HPP

// ShmemWriter from haidis_connectors (source/include/shmem_writer.hpp)
#include "shmem_writer.hpp"
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
 * ERSAP actor for processing HaidisGluexActor output and forwarding to shared memory.
 *
 * Input:
 *   - ARRAY_DOUBLE: duplets of doubles (value1, value2, ...)
 *     from HaidisGluexActor analysis
 *
 * Processing:
 *   1. Read input array and compute size metrics.
 *   2. Write to shared memory via ShmemWriter::write_data(in, 2, {duplet_count, 2}).
 *   3. Optionally print received duplets (verbose mode).
 *   4. Pass the ARRAY_DOUBLE through unchanged to downstream actors.
 *
 * Shared-memory layout written by ShmemWriter::write_data(in, 2, {duplet_count, 2}):
 *   Offset  Size       Field
 *   0        8         data_size (size_t) = num_doubles * sizeof(double)
 *   8        4         ndim (uint32_t) = 2
 *   12       4         dims[0] (uint32_t) = duplet_count
 *   16       4         dims[1] (uint32_t) = 2
 *   20       N*8       double[] raw payload
 *
 * Synchronization uses two POSIX named semaphores:
 *   sem_name     (data-ready, init 0): posted by writer after each write
 *   sem_ack_name (buffer-free, init 1): waited on by writer before each write,
 *                                       released by reader after consuming
 *
 * Configuration (JSON):
 *   "verbose"            : bool   - enable per-event logging              (default false)
 *   "enable_shmem_write" : bool   - enable shared memory writing          (default true)
 *   "shmem_name"         : string - POSIX shmem object name               (default "/haidis_gluex_shmem")
 *   "sem_name"           : string - data-ready semaphore name             (default "/haidis_gluex_sem")
 *   "sem_ack_name"       : string - buffer-free semaphore name            (default "/haidis_gluex_sem_ack")
 *   "shmem_size"         : int    - shared memory size bytes              (default 10485760 = 10 MB)
 *   "batch_size"         : int    - events to accumulate before writing   (default 1, must be > 0)
 *   "data_id_number"     : int    - number of distinct data IDs expected  (default 1, must be > 0)
 */
class HaidisGluexLinkActor : public ersap::Engine {
public:
    HaidisGluexLinkActor() = default;
    virtual ~HaidisGluexLinkActor() = default;

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
    // Configuration parameters (settable via JSON in configure())
    bool verbose_ = false;
    bool enable_shmem_write_ = true;  // Enable/disable shared memory writing
    std::string shmem_name_   = "/haidis_gluex_shmem";
    std::string sem_name_     = "/haidis_gluex_sem";
    std::string sem_ack_name_ = "/haidis_gluex_sem_ack"; // buffer-free (ack) semaphore
    std::size_t shmem_size_   = 10485760;                 // 10 MB default
    int batch_size_           = 1;   // events to accumulate per data_id before writing
    int data_id_number_       = 1;   // number of distinct data IDs expected

    // Shared memory writer — constructed in configure()
    std::unique_ptr<ShmemWriter> writer_;

    // Per-data-ID batch state — indexed by data_id (zero-based)
    std::vector<std::vector<double>> data_buffers_;
    std::vector<int>                 event_counts_;

    // Statistics
    std::size_t executeCallCount_ = 0;  // Track how many times execute() is called
    std::size_t eventCount_ = 0;
    std::size_t writeFailureCount_ = 0;
    std::size_t consecutiveFailures_ = 0;

    // Helper methods
    void printReceivedData(const std::vector<double>& data) const;
};

} // namespace coda
} // namespace ersap

// C interface for engine creation (required by ERSAP framework)
extern "C" std::unique_ptr<ersap::Engine> create_engine();

#endif // HAIDIS_GLUEX_LINK_ACTOR_HPP
