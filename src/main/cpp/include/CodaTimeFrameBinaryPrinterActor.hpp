/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * C++ mockup actor that receives CodaTimeFrame binary data type,
 * prints the binary content and structure, and returns the original event for pipeline continuation
 * @author gurjyan on 12/7/25
 * @project ersap-actor
*/

#ifndef CODA_TIME_FRAME_BINARY_PRINTER_ACTOR_HPP
#define CODA_TIME_FRAME_BINARY_PRINTER_ACTOR_HPP

#include <ersap/engine.hpp>
#include <ersap/engine_data.hpp>
#include <ersap/engine_data_type.hpp>
#include "CodaTImeFrameBinaryDataType.hpp"
#include <memory>
#include <set>
#include <string>
#include <vector>

namespace ersap {
namespace coda {

/**
 * C++ ERSAP engine that receives CodaTimeFrame binary data, prints detailed binary content,
 * and passes the event through unchanged for further processing.
 * 
 * Specializes in displaying binary serialization details and structure analysis.
 */
class CodaTimeFrameBinaryPrinterActor : public ersap::Engine {
public:
    CodaTimeFrameBinaryPrinterActor() = default;
    virtual ~CodaTimeFrameBinaryPrinterActor() = default;

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
    bool showBinaryDetails_ = true;
    bool showSerializationStats_ = true;
    std::int32_t maxBytesToShow_ = 256;
    bool showHexDump_ = false;
    
    // Statistics
    std::size_t eventCount_ = 0;
    std::size_t totalBinarySize_ = 0;
    
    // Helper methods for formatted output
    void printBinaryEventSummary(const CodaTimeFrame& event) const;
    void printSerializationDetails(const CodaTimeFrame& event) const;
    void printBinaryStructure(const std::vector<std::uint8_t>& buffer) const;
    void printHexDump(const std::vector<std::uint8_t>& buffer, std::size_t maxBytes) const;
    void printBinaryStatistics(const CodaTimeFrame& event) const;
    void printSeparator(const std::string& title = "") const;
    
    std::string formatBinarySize(std::size_t bytes) const;
    std::string formatHexByte(std::uint8_t byte) const;
    std::vector<std::uint8_t> serializeEvent(const CodaTimeFrame& event) const;
    std::size_t calculateExpectedSize(const CodaTimeFrame& event) const;
};

} // namespace coda
} // namespace ersap

// C interface for engine creation (required by ERSAP framework)
extern "C" std::unique_ptr<ersap::Engine> create_engine();

#endif // CODA_TIME_FRAME_BINARY_PRINTER_ACTOR_HPP