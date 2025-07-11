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
    // Create xMsg protobuf payload matching Java implementation
    xmsg::proto::Payload payload;
    
    // Add event type metadata
    auto* eventTypeItem = payload.add_item();
    eventTypeItem->set_name("event_type");
    eventTypeItem->mutable_data()->set_string("CodaTimeFrame");
    
    // Add time frame count
    auto* timeFrameCountItem = payload.add_item();
    timeFrameCountItem->set_name("time_frame_count");
    timeFrameCountItem->mutable_data()->set_vlsint32(static_cast<std::int32_t>(event.timeFrames.size()));
    
    // Serialize each time frame
    for (std::size_t tfIndex = 0; tfIndex < event.timeFrames.size(); ++tfIndex) {
        const auto& timeFrame = event.timeFrames[tfIndex];
        
        // Add ROC count for this time frame
        auto* rocCountItem = payload.add_item();
        rocCountItem->set_name("time_frame_" + std::to_string(tfIndex) + "_roc_count");
        rocCountItem->mutable_data()->set_vlsint32(static_cast<std::int32_t>(timeFrame.size()));
        
        // Serialize each ROC bank in this time frame
        for (std::size_t rocIndex = 0; rocIndex < timeFrame.size(); ++rocIndex) {
            const auto& rocBank = timeFrame[rocIndex];
            std::string rocPrefix = "time_frame_" + std::to_string(tfIndex) + "_roc_" + std::to_string(rocIndex);
            
            // ROC metadata
            auto* rocIdItem = payload.add_item();
            rocIdItem->set_name(rocPrefix + "_id");
            rocIdItem->mutable_data()->set_vlsint32(rocBank.rocId);
            
            auto* frameNumberItem = payload.add_item();
            frameNumberItem->set_name(rocPrefix + "_frame_number");
            frameNumberItem->mutable_data()->set_vlsint32(rocBank.frameNumber);
            
            auto* timestampItem = payload.add_item();
            timestampItem->set_name(rocPrefix + "_timestamp");
            timestampItem->mutable_data()->set_vlsint64(rocBank.timeStamp);
            
            // Hit count
            auto* hitCountItem = payload.add_item();
            hitCountItem->set_name(rocPrefix + "_hit_count");
            hitCountItem->mutable_data()->set_vlsint32(static_cast<std::int32_t>(rocBank.hits.size()));
            
            // Serialize hits if present
            if (!rocBank.hits.empty()) {
                // Pack all hit data into arrays for efficiency
                std::vector<std::int32_t> crates, slots, channels, charges;
                std::vector<std::int64_t> times;
                
                for (const auto& hit : rocBank.hits) {
                    crates.push_back(hit.crate);
                    slots.push_back(hit.slot);
                    channels.push_back(hit.channel);
                    charges.push_back(hit.charge);
                    times.push_back(hit.time);
                }
                
                // Add hit arrays
                auto* cratesItem = payload.add_item();
                cratesItem->set_name(rocPrefix + "_crates");
                for (std::int32_t crate : crates) {
                    cratesItem->mutable_data()->add_vlsint32a(crate);
                }
                
                auto* slotsItem = payload.add_item();
                slotsItem->set_name(rocPrefix + "_slots");
                for (std::int32_t slot : slots) {
                    slotsItem->mutable_data()->add_vlsint32a(slot);
                }
                
                auto* channelsItem = payload.add_item();
                channelsItem->set_name(rocPrefix + "_channels");
                for (std::int32_t channel : channels) {
                    channelsItem->mutable_data()->add_vlsint32a(channel);
                }
                
                auto* chargesItem = payload.add_item();
                chargesItem->set_name(rocPrefix + "_charges");
                for (std::int32_t charge : charges) {
                    chargesItem->mutable_data()->add_vlsint32a(charge);
                }
                
                auto* timesItem = payload.add_item();
                timesItem->set_name(rocPrefix + "_times");
                for (std::int64_t time : times) {
                    timesItem->mutable_data()->add_vlsint64a(time);
                }
            }
        }
    }
    
    // Serialize the payload to buffer
    buffer.resize(payload.ByteSizeLong());
    payload.SerializeToArray(buffer.data(), buffer.size());
}

CodaTimeFrame CodaTimeFrameSerializer::deserializeCodaTimeFrame(const std::vector<std::uint8_t>& buffer) const {
    // Check if this is a custom binary format (with COTF magic header)
    if (buffer.size() >= 4 && std::string(buffer.begin(), buffer.begin() + 4) == "COTF") {
        // Handle custom binary format (backward compatibility)
        std::size_t offset = 4;
        return deserializeCustomFormat(buffer, offset);
    }
    
    // Default to xMsg protobuf format
    return deserializeXMsgFormat(buffer);
}

CodaTimeFrame CodaTimeFrameSerializer::deserializeCustomFormat(const std::vector<std::uint8_t>& buffer, std::size_t& offset) const {
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

bool CodaTimeFrameSerializer::isXMsgProtobufFormat(const std::vector<std::uint8_t>& buffer) const {
    // xMsg protobuf format detection heuristics
    // Protobuf messages typically start with field tags (varint encoded)
    // The first byte usually indicates field number and wire type
    if (buffer.size() < 8) return false;
    
    // Check for protobuf wire format patterns
    // Field tags in protobuf are encoded as (field_number << 3) | wire_type
    // Wire types: 0=varint, 1=64-bit, 2=length-delimited, 3=start group, 4=end group, 5=32-bit
    uint8_t first_byte = buffer[0];
    uint8_t wire_type = first_byte & 0x7;
    
    // Valid wire types are 0-5, most commonly 0 (varint) and 2 (length-delimited)
    return (wire_type <= 5);
}

CodaTimeFrame CodaTimeFrameSerializer::deserializeXMsgFormat(const std::vector<std::uint8_t>& buffer) const {
    // Parse xMsg protobuf payload
    xmsg::proto::Payload payload;
    if (!payload.ParseFromArray(buffer.data(), buffer.size())) {
        throw std::runtime_error("Failed to parse xMsg protobuf payload");
    }
    
    CodaTimeFrame event;
    
    // Find metadata items
    int timeFrameCount = 0;
    for (const auto& item : payload.item()) {
        if (item.name() == "time_frame_count") {
            timeFrameCount = item.data().vlsint32();
            break;
        }
    }
    
    // Reconstruct time frames
    for (int tfIndex = 0; tfIndex < timeFrameCount; ++tfIndex) {
        TimeFrame timeFrame;
        
        // Find ROC count for this time frame
        int rocCount = 0;
        std::string rocCountKey = "time_frame_" + std::to_string(tfIndex) + "_roc_count";
        for (const auto& item : payload.item()) {
            if (item.name() == rocCountKey) {
                rocCount = item.data().vlsint32();
                break;
            }
        }
        
        // Reconstruct each ROC bank
        for (int rocIndex = 0; rocIndex < rocCount; ++rocIndex) {
            std::string rocPrefix = "time_frame_" + std::to_string(tfIndex) + "_roc_" + std::to_string(rocIndex);
            RocTimeFrameBank rocBank = reconstructRocBankFromXMsg(payload, rocPrefix);
            timeFrame.push_back(std::move(rocBank));
        }
        
        event.addTimeFrame(std::move(timeFrame));
    }
    
    return event;
}

RocTimeFrameBank CodaTimeFrameSerializer::reconstructRocBankFromXMsg(const xmsg::proto::Payload& payload, const std::string& rocPrefix) const {
    RocTimeFrameBank rocBank;
    
    // Extract ROC metadata
    for (const auto& item : payload.item()) {
        std::string name = item.name();
        if (name == rocPrefix + "_id") {
            rocBank.rocId = item.data().vlsint32();
        } else if (name == rocPrefix + "_frame_number") {
            rocBank.frameNumber = item.data().vlsint32();
        } else if (name == rocPrefix + "_timestamp") {
            rocBank.timeStamp = item.data().vlsint64();
        }
    }
    
    // Extract hit count
    int hitCount = 0;
    for (const auto& item : payload.item()) {
        if (item.name() == rocPrefix + "_hit_count") {
            hitCount = item.data().vlsint32();
            break;
        }
    }
    
    // Reconstruct hits if present
    if (hitCount > 0) {
        std::vector<std::int32_t> crates, slots, channels, charges;
        std::vector<std::int64_t> times;
        
        for (const auto& item : payload.item()) {
            std::string name = item.name();
            if (name == rocPrefix + "_crates") {
                for (int i = 0; i < item.data().vlsint32a_size(); ++i) {
                    crates.push_back(item.data().vlsint32a(i));
                }
            } else if (name == rocPrefix + "_slots") {
                for (int i = 0; i < item.data().vlsint32a_size(); ++i) {
                    slots.push_back(item.data().vlsint32a(i));
                }
            } else if (name == rocPrefix + "_channels") {
                for (int i = 0; i < item.data().vlsint32a_size(); ++i) {
                    channels.push_back(item.data().vlsint32a(i));
                }
            } else if (name == rocPrefix + "_charges") {
                for (int i = 0; i < item.data().vlsint32a_size(); ++i) {
                    charges.push_back(item.data().vlsint32a(i));
                }
            } else if (name == rocPrefix + "_times") {
                for (int i = 0; i < item.data().vlsint64a_size(); ++i) {
                    times.push_back(item.data().vlsint64a(i));
                }
            }
        }
        
        // Create FADCHit objects
        if (crates.size() == hitCount && slots.size() == hitCount && 
            channels.size() == hitCount && charges.size() == hitCount && 
            times.size() == hitCount) {
            for (int i = 0; i < hitCount; ++i) {
                FADCHit hit(crates[i], slots[i], channels[i], charges[i], times[i]);
                rocBank.addHit(hit);
            }
        }
    }
    
    return rocBank;
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