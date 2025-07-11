/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * Implementation of C++ TfEvent printer actor
 * @author gurjyan on 12/7/25
 * @project ersap-actor
*/

#include "CodaTimeFramePrinterActor.hpp"
#include "CodaTimeFrameDataType.hpp"
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
    return std::make_unique<ersap::coda::CodaTimeFramePrinterActor>();
}

namespace ersap {
namespace coda {

ersap::EngineData CodaTimeFramePrinterActor::configure(ersap::EngineData& input) {
    // Parse JSON configuration if provided
    if (input.mime_type() == ersap::type::JSON.mime_type()) {
        try {
            auto config = ersap::stdlib::parse_json(input);
            
            // Parse configuration options
            if (!config["verbose"].is_null()) {
                verbose_ = config["verbose"].bool_value();
            }
            if (!config["show_hit_details"].is_null()) {
                showHitDetails_ = config["show_hit_details"].bool_value();
            }
            if (!config["max_hits_to_show"].is_null()) {
                maxHitsToShow_ = config["max_hits_to_show"].int_value();
            }
            if (!config["show_timing_stats"].is_null()) {
                showTimingStats_ = config["show_timing_stats"].bool_value();
            }
            
            if (verbose_) {
                std::cout << "CodaTimeFramePrinterActor configured:" << std::endl;
                std::cout << "  - verbose: " << verbose_ << std::endl;
                std::cout << "  - show_hit_details: " << showHitDetails_ << std::endl;
                std::cout << "  - max_hits_to_show: " << maxHitsToShow_ << std::endl;
                std::cout << "  - show_timing_stats: " << showTimingStats_ << std::endl;
            }
        } catch (const std::exception& e) {
            std::cerr << "Error parsing configuration: " << e.what() << std::endl;
        }
    }
    
    return {};
}

ersap::EngineData CodaTimeFramePrinterActor::execute(ersap::EngineData& input) {
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
        
        // Print event content
        printSeparator("CodaTimeFrame Analysis");
        printEventSummary(event);
        
        if (showTimingStats_) {
            printTimingInfo(event);
        }
        
        if (verbose_) {
            printTimeFrameDetails(event);
        }
        
        if (showHitDetails_) {
            printHitSample(event);
        }
        
        printStatistics(event);
        printSeparator();
        
        // Update statistics
        //@todo Attention: the following two variables must be synchronized. It is not thread safe!
        eventCount_++;
        totalHitCount_ += event.getTotalHitCount();
        
        // Pass through the original event unchanged
        output.set_data(CODA_TIME_FRAME_TYPE, event);
        
    } catch (const std::exception& e) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Error processing CodaTimeFrame: " + std::string(e.what()));
        std::cerr << "Error in CodaTimeFramePrinterActor: " << e.what() << std::endl;
    }
    
    return output;
}

ersap::EngineData CodaTimeFramePrinterActor::execute_group(const std::vector<ersap::EngineData>&) {
    // Group processing not implemented for this demo actor
    return {};
}

std::vector<ersap::EngineDataType> CodaTimeFramePrinterActor::input_data_types() const {
    return { CODA_TIME_FRAME_TYPE, ersap::type::JSON };
}

std::vector<ersap::EngineDataType> CodaTimeFramePrinterActor::output_data_types() const {
    return { CODA_TIME_FRAME_TYPE, ersap::type::JSON };
}

std::set<std::string> CodaTimeFramePrinterActor::states() const {
    return {}; // Stateless engine
}

std::string CodaTimeFramePrinterActor::name() const {
    return "CodaTimeFramePrinterActor";
}

std::string CodaTimeFramePrinterActor::author() const {
    return "Jefferson Lab";
}

std::string CodaTimeFramePrinterActor::description() const {
    return "C++ mockup actor that prints CodaTimeFrame content and passes it through unchanged";
}

std::string CodaTimeFramePrinterActor::version() const {
    return "1.0.0";
}

// Private helper methods for formatted output

void CodaTimeFramePrinterActor::printEventSummary(const CodaTimeFrame& event) const {
    std::cout << "Event Summary:" << std::endl;
    std::cout << "  Event ID: " << event.eventId << std::endl;
    std::cout << "  Creation Time: " << formatTimestamp(event.creationTime) << std::endl;
    std::cout << "  Source Info: " << (event.sourceInfo.empty() ? "N/A" : event.sourceInfo) << std::endl;
    std::cout << "  Time Frames: " << event.getTimeFrameCount() << std::endl;
    std::cout << "  Total ROCs: " << event.getTotalRocCount() << std::endl;
    std::cout << "  Total Hits: " << event.getTotalHitCount() << std::endl;
    std::cout << "  Valid: " << (event.isValid() ? "Yes" : "No") << std::endl;
    std::cout << "  Empty: " << (event.isEmpty() ? "Yes" : "No") << std::endl;
    std::cout << std::endl;
}

void CodaTimeFramePrinterActor::printTimeFrameDetails(const CodaTimeFrame& event) const {
    std::cout << "Time Frame Details:" << std::endl;
    for (std::size_t tf = 0; tf < event.timeFrames.size(); ++tf) {
        const auto& timeFrame = event.timeFrames[tf];
        std::cout << "  Frame " << tf << ": " << timeFrame.size() << " ROCs" << std::endl;
        
        for (std::size_t roc = 0; roc < timeFrame.size(); ++roc) {
            const auto& rocBank = timeFrame[roc];
            std::cout << "    ROC " << rocBank.rocId 
                      << " (Frame #" << rocBank.frameNumber 
                      << ", Time: " << formatTimestamp(rocBank.timeStamp)
                      << ", Hits: " << rocBank.hits.size() << ")" << std::endl;
        }
    }
    std::cout << std::endl;
}

void CodaTimeFramePrinterActor::printHitSample(const CodaTimeFrame& event) const {
    auto allHits = event.getAllHits();
    std::cout << "Hit Sample (showing up to " << maxHitsToShow_ << " hits):" << std::endl;
    
    if (allHits.empty()) {
        std::cout << "  No hits found in event" << std::endl;
    } else {
        std::cout << "  " << std::setw(12) << "Crate-Slot-Ch" 
                  << std::setw(12) << "Charge" 
                  << std::setw(16) << "Time (ns)" 
                  << std::setw(10) << "ID" << std::endl;
        
        std::size_t hitCount = std::min(allHits.size(), static_cast<std::size_t>(maxHitsToShow_));
        for (std::size_t i = 0; i < hitCount; ++i) {
            const auto& hit = allHits[i];
            std::cout << "  " << std::setw(12) << hit.getName()
                      << std::setw(12) << hit.charge
                      << std::setw(16) << hit.time
                      << std::setw(10) << hit.getId() << std::endl;
        }
        
        if (allHits.size() > maxHitsToShow_) {
            std::cout << "  ... and " << (allHits.size() - maxHitsToShow_) << " more hits" << std::endl;
        }
    }
    std::cout << std::endl;
}

void CodaTimeFramePrinterActor::printTimingInfo(const CodaTimeFrame& event) const {
    if (event.timeFrames.empty()) {
        return;
    }
    
    // Find time range across all ROCs
    std::int64_t minTime = std::numeric_limits<std::int64_t>::max();
    std::int64_t maxTime = std::numeric_limits<std::int64_t>::min();
    
    for (const auto& timeFrame : event.timeFrames) {
        for (const auto& rocBank : timeFrame) {
            if (!rocBank.hits.empty()) {
                auto minHitTime = std::min_element(rocBank.hits.begin(), rocBank.hits.end(),
                    [](const FADCHit& a, const FADCHit& b) { return a.time < b.time; })->time;
                auto maxHitTime = std::max_element(rocBank.hits.begin(), rocBank.hits.end(),
                    [](const FADCHit& a, const FADCHit& b) { return a.time < b.time; })->time;
                
                minTime = std::min(minTime, minHitTime);
                maxTime = std::max(maxTime, maxHitTime);
            }
        }
    }
    
    if (minTime <= maxTime) {
        std::cout << "Timing Information:" << std::endl;
        std::cout << "  Earliest Hit: " << formatTimestamp(minTime) << std::endl;
        std::cout << "  Latest Hit: " << formatTimestamp(maxTime) << std::endl;
        std::cout << "  Time Span: " << formatDuration(maxTime - minTime) << std::endl;
        std::cout << std::endl;
    }
}

void CodaTimeFramePrinterActor::printStatistics(const CodaTimeFrame& event) const {
    std::cout << "Processing Statistics:" << std::endl;
    std::cout << "  Events Processed: " << eventCount_ << std::endl;
    std::cout << "  Total Hits Seen: " << totalHitCount_ << std::endl;
    if (eventCount_ > 0) {
        std::cout << "  Average Hits/Event: " << (totalHitCount_ / eventCount_) << std::endl;
    }
    std::cout << std::endl;
}

void CodaTimeFramePrinterActor::printSeparator(const std::string& title) const {
    const std::string separator(80, '=');
    std::cout << separator << std::endl;
    if (!title.empty()) {
        std::cout << " " << title << std::endl;
        std::cout << separator << std::endl;
    }
}

std::string CodaTimeFramePrinterActor::formatTimestamp(std::int64_t timestamp) const {
    if (timestamp == 0) {
        return "N/A";
    }
    
    // Convert nanoseconds to microseconds for readability
    double microseconds = timestamp / 1000.0;
    std::ostringstream oss;
    oss << std::fixed << std::setprecision(3) << microseconds << " μs";
    return oss.str();
}

std::string CodaTimeFramePrinterActor::formatDuration(std::int64_t nanoseconds) const {
    if (nanoseconds < 1000) {
        return std::to_string(nanoseconds) + " ns";
    } else if (nanoseconds < 1000000) {
        double microseconds = nanoseconds / 1000.0;
        std::ostringstream oss;
        oss << std::fixed << std::setprecision(3) << microseconds << " μs";
        return oss.str();
    } else {
        double milliseconds = nanoseconds / 1000000.0;
        std::ostringstream oss;
        oss << std::fixed << std::setprecision(3) << milliseconds << " ms";
        return oss.str();
    }
}

} // namespace coda
} // namespace ersap