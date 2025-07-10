/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * C++ mockup actor that receives CodaTimeFrame native data type,
 * prints the content, and returns the original event for pipeline continuation
 * @author gurjyan on 12/7/25
 * @project ersap-actor
*/

#ifndef CODA_TIME_FRAME_PRINTER_ACTOR_HPP
#define CODA_TIME_FRAME_PRINTER_ACTOR_HPP

#include <ersap/engine.hpp>
#include <ersap/engine_data.hpp>
#include <ersap/engine_data_type.hpp>
#include "CodaTimeFrameDataType.hpp"
#include <memory>
#include <set>
#include <string>
#include <vector>

namespace ersap {
namespace coda {

/**
 * C++ ERSAP engine that receives CodaTimeFrame data, prints detailed content,
 * and passes the event through unchanged for further processing.
 * 
 * Demonstrates cross-language data exchange between Java and C++ engines.
 */
class CodaTimeFramePrinterActor : public ersap::Engine {
public:
    CodaTimeFramePrinterActor() = default;
    virtual ~CodaTimeFramePrinterActor() = default;

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
    bool showHitDetails_ = true;
    std::int32_t maxHitsToShow_ = 100;
    bool showTimingStats_ = true;
    
    // Statistics
    std::size_t eventCount_ = 0;
    std::size_t totalHitCount_ = 0;
    
    // Helper methods for formatted output
    void printEventSummary(const CodaTimeFrame& event) const;
    void printTimeFrameDetails(const CodaTimeFrame& event) const;
    void printHitSample(const CodaTimeFrame& event) const;
    void printStatistics(const CodaTimeFrame& event) const;
    void printTimingInfo(const CodaTimeFrame& event) const;
    void printSeparator(const std::string& title = "") const;
    
    std::string formatTimestamp(std::int64_t timestamp) const;
    std::string formatDuration(std::int64_t nanoseconds) const;
};

} // namespace coda
} // namespace ersap

// C interface for engine creation (required by ERSAP framework)
extern "C" std::unique_ptr<ersap::Engine> create_engine();

#endif // CODA_TIME_FRAME_PRINTER_ACTOR_HPP