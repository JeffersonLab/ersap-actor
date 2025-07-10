/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * Implementation of C++ CodaTimeFrame serializer for cross-language communication
 * @author gurjyan on 12/7/25
 * @project ersap-actor
*/

#include "CodaTimeFrameDataType.hpp"
#include <ersap/any.hpp>
#include <cstring>
#include <stdexcept>
#include <algorithm>

namespace ersap {
namespace coda {

// Constants matching Java implementation
const std::string CODA_TIME_FRAME_MIME_TYPE = "xmsg/coda-time-frame";

const ersap::EngineDataType CODA_TIME_FRAME_TYPE{
    CODA_TIME_FRAME_MIME_TYPE,
    std::make_unique<CodaTimeFrameSerializer>()
};

// Serialization implementation
std::vector<std::uint8_t> CodaTimeFrameSerializer::write(const ersap::any& data) const {
    const auto& event = ersap::any_cast<const CodaTimeFrame&>(data);
    std::vector<std::uint8_t> buffer;
    serializeCodaTimeFrame(event, buffer);
    return buffer;
}

ersap::any CodaTimeFrameSerializer::read(const std::vector<std::uint8_t>& buffer) const {
    CodaTimeFrame event = deserializeCodaTimeFrame(buffer);
    return ersap::any{std::move(event)};
}

// Serialization helper methods
void CodaTimeFrameSerializer::serializeCodaTimeFrame(const CodaTimeFrame& event, std::vector<std::uint8_t>& buffer) const {
    // Serialize in simplified binary format for cross-language compatibility
    // This is a simplified protocol - in production, you'd want to use protobuf or xMsg payload
    
    // Write magic header for identification
    const std::string magic = "COTF";
    buffer.insert(buffer.end(), magic.begin(), magic.end());
    
    // Write version
    writeInt32(1, buffer); // Version 1
    
    // Write metadata
    writeInt64(event.eventId, buffer);
    writeInt64(event.creationTime, buffer);
    writeString(event.sourceInfo, buffer);
    
    // Write time frame count
    writeInt32(static_cast<std::int32_t>(event.timeFrames.size()), buffer);
    
    // Write each time frame
    for (const auto& timeFrame : event.timeFrames) {
        // Write ROC count for this time frame
        writeInt32(static_cast<std::int32_t>(timeFrame.size()), buffer);
        
        // Write each ROC bank
        for (const auto& rocBank : timeFrame) {
            writeInt32(rocBank.rocId, buffer);
            writeInt32(rocBank.frameNumber, buffer);
            writeInt64(rocBank.timeStamp, buffer);
            
            // Write hit count
            writeInt32(static_cast<std::int32_t>(rocBank.hits.size()), buffer);
            
            // Write hits in array format for efficiency
            if (!rocBank.hits.empty()) {
                std::vector<std::int32_t> crates, slots, channels, charges;
                std::vector<std::int64_t> times;
                
                for (const auto& hit : rocBank.hits) {
                    crates.push_back(hit.crate);
                    slots.push_back(hit.slot);
                    channels.push_back(hit.channel);
                    charges.push_back(hit.charge);
                    times.push_back(hit.time);
                }
                
                writeIntArray(crates, buffer);
                writeIntArray(slots, buffer);
                writeIntArray(channels, buffer);
                writeIntArray(charges, buffer);
                writeLongArray(times, buffer);
            }
        }
    }
}

CodaTimeFrame CodaTimeFrameSerializer::deserializeCodaTimeFrame(const std::vector<std::uint8_t>& buffer) const {
    std::size_t offset = 0;
    
    // Check magic header
    if (buffer.size() < 4 || 
        std::string(buffer.begin(), buffer.begin() + 4) != "COTF") {
        throw std::runtime_error("Invalid CodaTimeFrame buffer: missing magic header");
    }
    offset += 4;
    
    // Read version
    std::int32_t version = readInt32(buffer, offset);
    if (version != 1) {
        throw std::runtime_error("Unsupported CodaTimeFrame version: " + std::to_string(version));
    }
    
    CodaTimeFrame event;
    
    // Read metadata
    event.eventId = readInt64(buffer, offset);
    event.creationTime = readInt64(buffer, offset);
    event.sourceInfo = readString(buffer, offset);
    
    // Read time frame count
    std::int32_t timeFrameCount = readInt32(buffer, offset);
    
    // Read each time frame
    for (std::int32_t tf = 0; tf < timeFrameCount; ++tf) {
        TimeFrame timeFrame;
        
        // Read ROC count for this time frame
        std::int32_t rocCount = readInt32(buffer, offset);
        
        // Read each ROC bank
        for (std::int32_t roc = 0; roc < rocCount; ++roc) {
            RocTimeFrameBank rocBank;
            rocBank.rocId = readInt32(buffer, offset);
            rocBank.frameNumber = readInt32(buffer, offset);
            rocBank.timeStamp = readInt64(buffer, offset);
            
            // Read hit count
            std::int32_t hitCount = readInt32(buffer, offset);
            
            // Read hits from arrays
            if (hitCount > 0) {
                auto crates = readIntArray(buffer, offset);
                auto slots = readIntArray(buffer, offset);
                auto channels = readIntArray(buffer, offset);
                auto charges = readIntArray(buffer, offset);
                auto times = readLongArray(buffer, offset);
                
                // Reconstruct hits
                for (std::int32_t h = 0; h < hitCount; ++h) {
                    rocBank.addHit(FADCHit{crates[h], slots[h], channels[h], charges[h], times[h]});
                }
            }
            
            timeFrame.push_back(std::move(rocBank));
        }
        
        event.addTimeFrame(std::move(timeFrame));
    }
    
    return event;
}

// Primitive serialization methods
void CodaTimeFrameSerializer::writeInt32(std::int32_t value, std::vector<std::uint8_t>& buffer) const {
    buffer.push_back(static_cast<std::uint8_t>(value & 0xFF));
    buffer.push_back(static_cast<std::uint8_t>((value >> 8) & 0xFF));
    buffer.push_back(static_cast<std::uint8_t>((value >> 16) & 0xFF));
    buffer.push_back(static_cast<std::uint8_t>((value >> 24) & 0xFF));
}

void CodaTimeFrameSerializer::writeInt64(std::int64_t value, std::vector<std::uint8_t>& buffer) const {
    for (int i = 0; i < 8; ++i) {
        buffer.push_back(static_cast<std::uint8_t>((value >> (i * 8)) & 0xFF));
    }
}

void CodaTimeFrameSerializer::writeString(const std::string& str, std::vector<std::uint8_t>& buffer) const {
    writeInt32(static_cast<std::int32_t>(str.length()), buffer);
    buffer.insert(buffer.end(), str.begin(), str.end());
}

void CodaTimeFrameSerializer::writeIntArray(const std::vector<std::int32_t>& array, std::vector<std::uint8_t>& buffer) const {
    writeInt32(static_cast<std::int32_t>(array.size()), buffer);
    for (std::int32_t value : array) {
        writeInt32(value, buffer);
    }
}

void CodaTimeFrameSerializer::writeLongArray(const std::vector<std::int64_t>& array, std::vector<std::uint8_t>& buffer) const {
    writeInt32(static_cast<std::int32_t>(array.size()), buffer);
    for (std::int64_t value : array) {
        writeInt64(value, buffer);
    }
}

std::int32_t CodaTimeFrameSerializer::readInt32(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const {
    if (offset + 4 > buffer.size()) {
        throw std::runtime_error("Buffer underflow reading int32");
    }
    
    std::int32_t value = static_cast<std::int32_t>(buffer[offset]) |
                        (static_cast<std::int32_t>(buffer[offset + 1]) << 8) |
                        (static_cast<std::int32_t>(buffer[offset + 2]) << 16) |
                        (static_cast<std::int32_t>(buffer[offset + 3]) << 24);
    offset += 4;
    return value;
}

std::int64_t CodaTimeFrameSerializer::readInt64(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const {
    if (offset + 8 > buffer.size()) {
        throw std::runtime_error("Buffer underflow reading int64");
    }
    
    std::int64_t value = 0;
    for (int i = 0; i < 8; ++i) {
        value |= (static_cast<std::int64_t>(buffer[offset + i]) << (i * 8));
    }
    offset += 8;
    return value;
}

std::string CodaTimeFrameSerializer::readString(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const {
    std::int32_t length = readInt32(buffer, offset);
    if (length < 0 || offset + length > buffer.size()) {
        throw std::runtime_error("Invalid string length or buffer underflow");
    }
    
    std::string result(buffer.begin() + offset, buffer.begin() + offset + length);
    offset += length;
    return result;
}

std::vector<std::int32_t> CodaTimeFrameSerializer::readIntArray(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const {
    std::int32_t length = readInt32(buffer, offset);
    std::vector<std::int32_t> result;
    result.reserve(length);
    
    for (std::int32_t i = 0; i < length; ++i) {
        result.push_back(readInt32(buffer, offset));
    }
    
    return result;
}

std::vector<std::int64_t> CodaTimeFrameSerializer::readLongArray(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const {
    std::int32_t length = readInt32(buffer, offset);
    std::vector<std::int64_t> result;
    result.reserve(length);
    
    for (std::int32_t i = 0; i < length; ++i) {
        result.push_back(readInt64(buffer, offset));
    }
    
    return result;
}

} // namespace coda
} // namespace ersap