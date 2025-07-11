/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * Implementation of C++ CodaTimeFrame binary printer actor
 * @author gurjyan on 12/7/25
 * @project ersap-actor
*/

#include "CodaTimeFrameBinaryPrinterActor.hpp"
#include "CodaTimeFrameBinaryDataType.hpp"
#include <ersap/stdlib/json_utils.hpp>
#include <iostream>
#include <iomanip>
#include <sstream>
#include <algorithm>
#include <chrono>

// C interface for engine creation
extern "C"
std::unique_ptr<ersap::Engine> create_engine()
{
    return std::make_unique<ersap::coda::CodaTimeFrameBinaryPrinterActor>();
}

namespace ersap {
namespace coda {

ersap::EngineData CodaTimeFrameBinaryPrinterActor::configure(ersap::EngineData& input) {
    // Parse JSON configuration if provided
    if (input.mime_type() == ersap::type::JSON.mime_type()) {
        try {
            auto config = ersap::stdlib::parse_json(input);
            
            // Parse configuration options
            if (!config["verbose"].is_null()) {
                verbose_ = config["verbose"].bool_value();
            }
            if (!config["show_binary_details"].is_null()) {
                showBinaryDetails_ = config["show_binary_details"].bool_value();
            }
            if (!config["max_bytes_to_show"].is_null()) {
                maxBytesToShow_ = config["max_bytes_to_show"].int_value();
            }
            if (!config["show_serialization_stats"].is_null()) {
                showSerializationStats_ = config["show_serialization_stats"].bool_value();
            }
            if (!config["show_hex_dump"].is_null()) {
                showHexDump_ = config["show_hex_dump"].bool_value();
            }
            
            if (verbose_) {
                std::cout << "CodaTimeFrameBinaryPrinterActor configured:" << std::endl;
                std::cout << "  - verbose: " << verbose_ << std::endl;
                std::cout << "  - show_binary_details: " << showBinaryDetails_ << std::endl;
                std::cout << "  - max_bytes_to_show: " << maxBytesToShow_ << std::endl;
                std::cout << "  - show_serialization_stats: " << showSerializationStats_ << std::endl;
                std::cout << "  - show_hex_dump: " << showHexDump_ << std::endl;
            }
        } catch (const std::exception& e) {
            std::cerr << "Error parsing configuration: " << e.what() << std::endl;
        }
    }
    
    return {};
}

ersap::EngineData CodaTimeFrameBinaryPrinterActor::execute(ersap::EngineData& input) {
    auto output = ersap::EngineData{};

    // Verify input data type
    if (input.mime_type() != CODA_TIME_FRAME_MIME_TYPE) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Wrong input type: expected " + CODA_TIME_FRAME_MIME_TYPE + 
                               ", got " + input.mime_type());
        return output;
    }
    
    try {
        // Extract CodaTimeFrame from input
        const auto& event = ersap::data_cast<const CodaTimeFrame&>(input);
        
        // Print binary event content
        printSeparator("CodaTimeFrame Binary Analysis");
        printBinaryEventSummary(event);
        
        if (showSerializationStats_) {
            printSerializationDetails(event);
        }
        
        if (showBinaryDetails_) {
            auto binaryData = serializeEvent(event);
            printBinaryStructure(binaryData);
            
            if (showHexDump_) {
                printHexDump(binaryData, maxBytesToShow_);
            }
        }
        
        printBinaryStatistics(event);
        printSeparator();
        
        // Update statistics
        eventCount_++;
        totalBinarySize_ += serializeEvent(event).size();
        
        // Pass through the original event unchanged
        output.set_data(CODA_TIME_FRAME_TYPE, event);
        
    } catch (const std::exception& e) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Error processing CodaTimeFrame: " + std::string(e.what()));
        std::cerr << "Error in CodaTimeFrameBinaryPrinterActor: " << e.what() << std::endl;
    }
    
    return output;
}

ersap::EngineData CodaTimeFrameBinaryPrinterActor::execute_group(const std::vector<ersap::EngineData>&) {
    // Group processing not implemented for this demo actor
    return {};
}

std::vector<ersap::EngineDataType> CodaTimeFrameBinaryPrinterActor::input_data_types() const {
    return { CODA_TIME_FRAME_TYPE, ersap::type::JSON };
}

std::vector<ersap::EngineDataType> CodaTimeFrameBinaryPrinterActor::output_data_types() const {
    return { CODA_TIME_FRAME_TYPE, ersap::type::JSON };
}

std::set<std::string> CodaTimeFrameBinaryPrinterActor::states() const {
    return {}; // Stateless engine
}

std::string CodaTimeFrameBinaryPrinterActor::name() const {
    return "CodaTimeFrameBinaryPrinterActor";
}

std::string CodaTimeFrameBinaryPrinterActor::author() const {
    return "Jefferson Lab";
}

std::string CodaTimeFrameBinaryPrinterActor::description() const {
    return "C++ actor that prints CodaTimeFrame binary serialization details and passes it through unchanged";
}

std::string CodaTimeFrameBinaryPrinterActor::version() const {
    return "1.0.0";
}

// Private helper methods

void CodaTimeFrameBinaryPrinterActor::printBinaryEventSummary(const CodaTimeFrame& event) const {
    std::cout << "Binary Event Summary:" << std::endl;
    std::cout << "  Event ID: " << event.eventId << std::endl;
    std::cout << "  Creation Time: " << event.creationTime << std::endl;
    std::cout << "  Source Info: " << event.sourceInfo << std::endl;
    std::cout << "  Time Frames: " << event.getTimeFrameCount() << std::endl;
    std::cout << "  Total ROCs: " << event.getTotalRocCount() << std::endl;
    std::cout << "  Total Hits: " << event.getTotalHitCount() << std::endl;
    
    auto binarySize = serializeEvent(event).size();
    std::cout << "  Binary Size: " << formatBinarySize(binarySize) << std::endl;
}

void CodaTimeFrameBinaryPrinterActor::printSerializationDetails(const CodaTimeFrame& event) const {
    std::cout << "Serialization Details:" << std::endl;
    
    auto binaryData = serializeEvent(event);
    auto expectedSize = calculateExpectedSize(event);
    
    std::cout << "  Serialized Size: " << formatBinarySize(binaryData.size()) << std::endl;
    std::cout << "  Expected Size: " << formatBinarySize(expectedSize) << std::endl;
    
    double efficiency = (expectedSize > 0) ? (double)binaryData.size() / expectedSize * 100.0 : 0.0;
    std::cout << "  Serialization Efficiency: " << std::fixed << std::setprecision(2) << efficiency << "%" << std::endl;
    
    // Calculate per-hit overhead
    if (event.getTotalHitCount() > 0) {
        double bytesPerHit = (double)binaryData.size() / event.getTotalHitCount();
        std::cout << "  Bytes per Hit: " << std::fixed << std::setprecision(2) << bytesPerHit << std::endl;
    }
}

void CodaTimeFrameBinaryPrinterActor::printBinaryStructure(const std::vector<std::uint8_t>& buffer) const {
    std::cout << "Binary Structure Analysis:" << std::endl;
    std::cout << "  Total Buffer Size: " << formatBinarySize(buffer.size()) << std::endl;
    
    if (buffer.size() >= 4) {
        // Read number of time frames (first 4 bytes)
        std::int32_t timeFrameCount = 0;
        for (int i = 0; i < 4; ++i) {
            timeFrameCount |= (buffer[i] << (i * 8));
        }
        std::cout << "  Time Frame Count (from binary): " << timeFrameCount << std::endl;
        
        // Analyze header size
        std::size_t headerSize = 4; // Initial time frame count
        std::cout << "  Header Size: " << formatBinarySize(headerSize) << std::endl;
        std::cout << "  Data Size: " << formatBinarySize(buffer.size() - headerSize) << std::endl;
    }
}

void CodaTimeFrameBinaryPrinterActor::printHexDump(const std::vector<std::uint8_t>& buffer, std::size_t maxBytes) const {
    std::cout << "Hex Dump (first " << std::min(maxBytes, buffer.size()) << " bytes):" << std::endl;
    
    std::size_t bytesToShow = std::min(maxBytes, buffer.size());
    const std::size_t bytesPerLine = 16;
    
    for (std::size_t i = 0; i < bytesToShow; i += bytesPerLine) {
        // Print offset
        std::cout << "  " << std::setfill('0') << std::setw(8) << std::hex << i << ": ";
        
        // Print hex bytes
        for (std::size_t j = 0; j < bytesPerLine; ++j) {
            if (i + j < bytesToShow) {
                std::cout << formatHexByte(buffer[i + j]) << " ";
            } else {
                std::cout << "   ";
            }
        }
        
        std::cout << " |";
        
        // Print ASCII representation
        for (std::size_t j = 0; j < bytesPerLine && i + j < bytesToShow; ++j) {
            std::uint8_t byte = buffer[i + j];
            if (byte >= 32 && byte <= 126) {
                std::cout << static_cast<char>(byte);
            } else {
                std::cout << ".";
            }
        }
        
        std::cout << "|" << std::endl;
    }
    
    std::cout << std::dec; // Reset to decimal
}

void CodaTimeFrameBinaryPrinterActor::printBinaryStatistics(const CodaTimeFrame& event) const {
    std::cout << "Binary Statistics:" << std::endl;
    std::cout << "  Events processed: " << eventCount_ << std::endl;
    std::cout << "  Total binary data: " << formatBinarySize(totalBinarySize_) << std::endl;
    
    if (eventCount_ > 0) {
        double avgSize = (double)totalBinarySize_ / eventCount_;
        std::cout << "  Average event size: " << formatBinarySize(static_cast<std::size_t>(avgSize)) << std::endl;
    }
}

void CodaTimeFrameBinaryPrinterActor::printSeparator(const std::string& title) const {
    std::string separator(60, '=');
    std::cout << separator << std::endl;
    if (!title.empty()) {
        std::cout << title << std::endl;
        std::cout << separator << std::endl;
    }
}

std::string CodaTimeFrameBinaryPrinterActor::formatBinarySize(std::size_t bytes) const {
    if (bytes < 1024) {
        return std::to_string(bytes) + " B";
    } else if (bytes < 1024 * 1024) {
        return std::to_string(bytes / 1024) + " KB";
    } else {
        return std::to_string(bytes / (1024 * 1024)) + " MB";
    }
}

std::string CodaTimeFrameBinaryPrinterActor::formatHexByte(std::uint8_t byte) const {
    std::ostringstream oss;
    oss << std::hex << std::setfill('0') << std::setw(2) << static_cast<int>(byte);
    return oss.str();
}

std::vector<std::uint8_t> CodaTimeFrameBinaryPrinterActor::serializeEvent(const CodaTimeFrame& event) const {
    return CodaTimeFrameSerializer::serializeToBinary(event);
}

std::size_t CodaTimeFrameBinaryPrinterActor::calculateExpectedSize(const CodaTimeFrame& event) const {
    std::size_t size = 0;
    
    // Time frame count (4 bytes)
    size += 4;
    
    for (const auto& timeFrame : event.timeFrames) {
        // ROC count per time frame (4 bytes)
        size += 4;
        
        for (const auto& roc : timeFrame) {
            // ROC header: rocId (4) + frameNumber (4) + timeStamp (8) + hit count (4)
            size += 20;
            
            // Hits data: each hit has 4 int32 (16 bytes) + 1 int64 (8 bytes) = 24 bytes
            size += roc.hits.size() * 24;
        }
    }
    
    return size;
}

} // namespace coda
} // namespace ersap