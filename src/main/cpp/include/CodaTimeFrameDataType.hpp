/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * C++ data structures and serializer for ERSAP CodaTimeFrame 
 * Compatible with Java implementation for cross-language communication
 * @author gurjyan on 12/7/25
 * @project ersap-actor
 */

#ifndef CODA_TIME_FRAME_DATA_TYPE_HPP
#define CODA_TIME_FRAME_DATA_TYPE_HPP

#include <ersap/engine_data_type.hpp>
#include <ersap/serializer.hpp>
#include <xmsg/proto/data.h>
#include <vector>
#include <memory>
#include <string>
#include <cstdint>
#include <algorithm>

namespace xmsg {
namespace proto {
    class Payload;
}
}

namespace ersap {
namespace coda {

/**
 * Represents a single FADC hit from detector hardware
 * Matches the Java FADCHit structure
 */
struct FADCHit {
    std::int32_t crate;     // Crate number in DAQ system
    std::int32_t slot;      // Slot number within crate  
    std::int32_t channel;   // Channel number within slot
    std::int32_t charge;    // Integrated ADC charge value
    std::int64_t time;      // Hit timestamp in nanoseconds

    FADCHit() = default;


    FADCHit(std::int32_t c, std::int32_t s, std::int32_t ch, 
            std::int32_t charge, std::int64_t t)
        : crate(c), slot(s), channel(ch), charge(charge), time(t) {}

    // Generate unique identifier string (crate-slot-channel)
    std::string getName() const {
        return std::to_string(crate) + "-" + std::to_string(slot) + "-" + std::to_string(channel);
    }

    // Generate unique numeric ID
    std::int32_t getId() const {
        return (crate * 1000) + (slot * 16) + channel;
    }
};

/**
 * Represents a collection of hits from a single ROC within a time frame
 * Matches the Java RocTimeFrameBank structure
 */
struct RocTimeFrameBank {
    std::int32_t rocId;         // ReadOut Controller identifier
    std::int32_t frameNumber;   // Time frame sequence number
    std::int64_t timeStamp;     // Frame timestamp in nanoseconds
    std::vector<FADCHit> hits;  // Collection of hits within this time frame

    RocTimeFrameBank() = default;

    RocTimeFrameBank(std::int32_t roc, std::int32_t frame, std::int64_t ts)
        : rocId(roc), frameNumber(frame), timeStamp(ts) {}

    void addHit(const FADCHit& hit) {
        hits.push_back(hit);
    }

    void addHits(const std::vector<FADCHit>& hitList) {
        hits.insert(hits.end(), hitList.begin(), hitList.end());
    }

    std::size_t getHitCount() const {
        return hits.size();
    }
};

/**
 * Container for a single time frame containing multiple ROC banks
 * Matches the Java TimeFrame structure
 */
using TimeFrame = std::vector<RocTimeFrameBank>;

/**
 * Represents a complete physics event containing multiple time frames
 * Matches the Java EtEvent structure
 */
struct CodaTimeFrame {
    std::vector<TimeFrame> timeFrames;  // Nested structure: timeFrames[i][j] = ROC bank j in time frame i
    
    // Optional metadata
    std::int64_t eventId = 0;           // Unique event identifier
    std::int64_t creationTime = 0;      // Event creation timestamp
    std::string sourceInfo;             // Source information (ET system, file, etc.)

    CodaTimeFrame() = default;

    void addTimeFrame(const TimeFrame& timeFrame) {
        timeFrames.push_back(timeFrame);
    }

    void addRocToCurrentTimeFrame(const RocTimeFrameBank& rocBank) {
        if (timeFrames.empty()) {
            timeFrames.emplace_back();
        }
        timeFrames.back().push_back(rocBank);
    }

    void startNewTimeFrame() {
        timeFrames.emplace_back();
    }

    // Utility methods
    std::size_t getTimeFrameCount() const {
        return timeFrames.size();
    }

    std::size_t getTotalRocCount() const {
        std::size_t count = 0;
        for (const auto& timeFrame : timeFrames) {
            count += timeFrame.size();
        }
        return count;
    }

    std::size_t getTotalHitCount() const {
        std::size_t count = 0;
        for (const auto& timeFrame : timeFrames) {
            for (const auto& rocBank : timeFrame) {
                count += rocBank.hits.size();
            }
        }
        return count;
    }

    // Get all hits as a flat vector
    std::vector<FADCHit> getAllHits() const {
        std::vector<FADCHit> allHits;
        for (const auto& timeFrame : timeFrames) {
            for (const auto& rocBank : timeFrame) {
                allHits.insert(allHits.end(), rocBank.hits.begin(), rocBank.hits.end());
            }
        }
        return allHits;
    }

    // Get all ROC banks as a flat vector
    std::vector<RocTimeFrameBank> getAllRocBanks() const {
        std::vector<RocTimeFrameBank> allRocs;
        for (const auto& timeFrame : timeFrames) {
            allRocs.insert(allRocs.end(), timeFrame.begin(), timeFrame.end());
        }
        return allRocs;
    }

    bool isEmpty() const {
        return timeFrames.empty() || 
               std::all_of(timeFrames.begin(), timeFrames.end(), 
                          [](const TimeFrame& tf) { return tf.empty(); });
    }

    bool isValid() const {
        for (const auto& timeFrame : timeFrames) {
            for (const auto& rocBank : timeFrame) {
                // Basic validation - could be extended
                if (rocBank.frameNumber < 0 || rocBank.timeStamp < 0) {
                    return false;
                }
            }
        }
        return true;
    }
};

/**
 * Custom serializer for CodaTimeFrame to xMsg native format
 * Enables cross-language communication with Java ERSAP engines
 */
class CodaTimeFrameSerializer : public ersap::Serializer {
public:
    std::vector<std::uint8_t> write(const ersap::any& data) const override;
    ersap::any read(const std::vector<std::uint8_t>& buffer) const override;

private:
    // Helper methods for xMsg payload serialization
    void serializeCodaTimeFrame(const CodaTimeFrame& event, std::vector<std::uint8_t>& buffer) const;
    CodaTimeFrame deserializeCodaTimeFrame(const std::vector<std::uint8_t>& buffer) const;
    
    // Format-specific deserialization methods
    CodaTimeFrame deserializeCustomFormat(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const;
    CodaTimeFrame deserializeXMsgFormat(const std::vector<std::uint8_t>& buffer) const;
    bool isXMsgProtobufFormat(const std::vector<std::uint8_t>& buffer) const;
    RocTimeFrameBank reconstructRocBankFromXMsg(const xmsg::proto::Payload& payload, const std::string& rocPrefix) const;
    
    // Helper methods for primitive data serialization
    void writeInt32(std::int32_t value, std::vector<std::uint8_t>& buffer) const;
    void writeInt64(std::int64_t value, std::vector<std::uint8_t>& buffer) const;
    void writeString(const std::string& str, std::vector<std::uint8_t>& buffer) const;
    void writeIntArray(const std::vector<std::int32_t>& array, std::vector<std::uint8_t>& buffer) const;
    void writeLongArray(const std::vector<std::int64_t>& array, std::vector<std::uint8_t>& buffer) const;
    
    std::int32_t readInt32(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const;
    std::int64_t readInt64(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const;
    std::string readString(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const;
    std::vector<std::int32_t> readIntArray(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const;
    std::vector<std::int64_t> readLongArray(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const;
};

// MIME type constant matching Java implementation
extern const std::string CODA_TIME_FRAME_MIME_TYPE;

// The ERSAP EngineDataType for CodaTimeFrame
extern const ersap::EngineDataType CODA_TIME_FRAME_TYPE;

} // namespace coda
} // namespace ersap

#endif // CODA_TIME_FRAME_DATA_TYPE_HPP