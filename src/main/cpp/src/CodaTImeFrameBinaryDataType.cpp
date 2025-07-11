// CodaTimeFrameBinaryDataType.cpp
#include "CodaTimeFrameBinaryDataType.hpp"
#include <stdexcept>
#include <cstring>
#include <cstdint>
#include <vector>

namespace ersap {
namespace coda {


std::vector<std::uint8_t> CodaTimeFrameSerializer::serializeToBinary(const CodaTimeFrame& event) {
    std::vector<std::uint8_t> buffer;

    auto writeInt32 = [&](std::int32_t val) {
        for (int i = 0; i < 4; ++i)
            buffer.push_back(static_cast<uint8_t>((val >> (i * 8)) & 0xFF));
    };

    auto writeInt64 = [&](std::int64_t val) {
        for (int i = 0; i < 8; ++i)
            buffer.push_back(static_cast<uint8_t>((val >> (i * 8)) & 0xFF));
    };

    const auto& timeFrames = event.timeFrames;
    writeInt32(static_cast<std::int32_t>(timeFrames.size()));

    for (const auto& tf : timeFrames) {
        writeInt32(static_cast<std::int32_t>(tf.size()));

        for (const auto& roc : tf) {
            writeInt32(roc.rocId);
            writeInt32(roc.frameNumber);
            writeInt64(roc.timeStamp);

            const auto& hits = roc.hits;
            writeInt32(static_cast<std::int32_t>(hits.size()));

            for (const auto& h : hits) writeInt32(h.crate);
            for (const auto& h : hits) writeInt32(h.slot);
            for (const auto& h : hits) writeInt32(h.channel);
            for (const auto& h : hits) writeInt32(h.charge);
            for (const auto& h : hits) writeInt64(h.time);
        }
    }

    return buffer;
}

CodaTimeFrame CodaTimeFrameSerializer::deserializeFromBinary(const std::vector<std::uint8_t>& buffer) {
    size_t offset = 0;

    auto readInt32 = [&]() -> std::int32_t {
        if (offset + 4 > buffer.size()) throw std::runtime_error("Underflow int32");
        std::int32_t val = 0;
        for (int i = 0; i < 4; ++i)
            val |= (buffer[offset++] << (i * 8));
        return val;
    };

    auto readInt64 = [&]() -> std::int64_t {
        if (offset + 8 > buffer.size()) throw std::runtime_error("Underflow int64");
        std::int64_t val = 0;
        for (int i = 0; i < 8; ++i)
            val |= (static_cast<std::int64_t>(buffer[offset++]) << (i * 8));
        return val;
    };

    CodaTimeFrame event;
    int tfCount = readInt32();

    for (int t = 0; t < tfCount; ++t) {
        TimeFrame tf;
        int rocCount = readInt32();

        for (int r = 0; r < rocCount; ++r) {
            RocTimeFrameBank roc;
            roc.rocId = readInt32();
            roc.frameNumber = readInt32();
            roc.timeStamp = readInt64();

            int hitCount = readInt32();
            std::vector<std::int32_t> crates(hitCount), slots(hitCount), chans(hitCount), charges(hitCount);
            std::vector<std::int64_t> times(hitCount);

            for (int i = 0; i < hitCount; ++i) crates[i] = readInt32();
            for (int i = 0; i < hitCount; ++i) slots[i] = readInt32();
            for (int i = 0; i < hitCount; ++i) chans[i] = readInt32();
            for (int i = 0; i < hitCount; ++i) charges[i] = readInt32();
            for (int i = 0; i < hitCount; ++i) times[i] = readInt64();

            for (int i = 0; i < hitCount; ++i)
                roc.addHit(FADCHit{crates[i], slots[i], chans[i], charges[i], times[i]});

            tf.push_back(std::move(roc));
        }
        event.addTimeFrame(tf);
    }

    return event;
}

// CodaTimeFrameSerializer implementation
std::vector<std::uint8_t> CodaTimeFrameSerializer::write(const ersap::any& data) const {
    const auto& event = ersap::any_cast<const CodaTimeFrame&>(data);
    return CodaTimeFrameSerializer::serializeToBinary(event);
}

ersap::any CodaTimeFrameSerializer::read(const std::vector<std::uint8_t>& buffer) const {
    CodaTimeFrame event = CodaTimeFrameSerializer::deserializeFromBinary(buffer);
    return ersap::any{std::move(event)};
}

} // namespace coda
} // namespace ersap
