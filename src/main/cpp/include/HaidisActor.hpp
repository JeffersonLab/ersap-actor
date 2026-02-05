/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * ERSAP actor that interfaces with ET system to process physics event data.
 * Receives SINT32 trigger from Java source, reads events from ET system,
 * and processes four-vector physics data (π+, π-, γ1, γ2).
 *
 * @author gurjyan
 * @project ersap-actor
 */

#ifndef HAIDIS_ACTOR_HPP
#define HAIDIS_ACTOR_HPP

#include <ersap/engine.hpp>
#include <ersap/engine_data.hpp>
#include <ersap/engine_data_type.hpp>
#include <et.h>
#include <string>
#include <memory>
#include <set>
#include <vector>

namespace ersap {
namespace coda {

/**
 * ERSAP engine that bridges Java trigger signals to ET system event processing.
 *
 * Architecture:
 * 1. Java UniAdapterSourceEngine sends SINT32 trigger value
 * 2. HaidisActor validates trigger and reads next event from ET system
 * 3. Processes physics data (16 doubles representing four-vectors)
 * 4. Returns event to ET system and outputs result
 *
 * The SINT32 input serves solely as a trigger signal and carries no semantic data.
 */
class HaidisActor : public ersap::Engine {
public:
    HaidisActor() = default;
    virtual ~HaidisActor();

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
    std::string etFilename_;
    std::string etHost_ = "localhost";
    int etPort_ = ET_SERVER_PORT;
    std::string stationName_ = "ERSAP_PROCESSOR";

    // ET system handles
    et_sys_id etSys_ = nullptr;
    et_att_id etAtt_ = 0;
    et_stat_id etStat_ = 0;
    bool etConnected_ = false;

    // Statistics
    std::size_t eventCount_ = 0;
    std::size_t errorCount_ = 0;

    // Expected physics data size: 16 doubles for four 4-vectors
    static constexpr std::size_t EXPECTED_SIZE = 16 * sizeof(double);

    // Helper methods for ET system management
    bool initializeET();
    void cleanupET();

    // Helper methods for data processing and output
    void printFourVectors(const double* data) const;
    void printEventSummary() const;
    void printSeparator(const std::string& title = "") const;
};

} // namespace coda
} // namespace ersap

// C interface for engine creation (required by ERSAP framework)
extern "C" std::unique_ptr<ersap::Engine> create_engine();

#endif // HAIDIS_ACTOR_HPP
