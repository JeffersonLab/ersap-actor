// CodaTimeFrameBinaryDataType.cpp
//
// Wire format of "binary/coda-time-frame". This must stay byte-for-byte
// identical to
//   org.jlab.ersap.actor.datatypes.CodaTimeFrameBinaryDataType
// in ersap-actor's Java tree, which is the incumbent producer and consumer of
// this MIME type inside ERSAP.
//
//   int32  timeFrameCount
//   repeat timeFrameCount:
//     int32  rocCount
//     repeat rocCount:
//       int32  rocId
//       int32  frameNumber
//       int64  timeStamp
//       int32  hitCount
//       int32  crate  [hitCount]      -- struct of arrays, not array of structs
//       int32  slot   [hitCount]
//       int32  channel[hitCount]
//       int32  charge [hitCount]
//       int64  time   [hitCount]
//
// All integers are BIG-ENDIAN, which is what the Java side writes with
// DataOutputStream and reads with a ByteBuffer in ByteOrder.BIG_ENDIAN. This
// file used to write and read them little-endian, which made every C++ payload
// unreadable by a Java actor and vice versa; see the note in
// pet-sro/cpp/src/actor/README.md.
//
// There is no length prefix, no envelope and no header: the buffer starts at
// timeFrameCount and ends with the last hit. CodaTimeFrame::eventId,
// creationTime and sourceInfo have no counterpart in the Java EtEvent and are
// deliberately not serialized.

#include "CodaTImeFrameBinaryDataType.hpp"
#include <stdexcept>
#include <cstring>
#include <cstdint>
#include <vector>

namespace ersap {
namespace coda {

namespace {

/// Largest count accepted from a buffer before it is called corrupt. Without
/// this a garbage length word makes the reader try to allocate exabytes.
constexpr std::size_t MAX_ELEMENT_COUNT = 100000000;

}  // namespace

std::vector<std::uint8_t> CodaTimeFrameSerializer::serializeToBinary(const CodaTimeFrame& event) {
    std::vector<std::uint8_t> buffer;

    auto writeInt32 = [&](std::int32_t val) {
        const auto u = static_cast<std::uint32_t>(val);
        for (int i = 3; i >= 0; --i)
            buffer.push_back(static_cast<std::uint8_t>((u >> (i * 8)) & 0xFFu));
    };

    auto writeInt64 = [&](std::int64_t val) {
        const auto u = static_cast<std::uint64_t>(val);
        for (int i = 7; i >= 0; --i)
            buffer.push_back(static_cast<std::uint8_t>((u >> (i * 8)) & 0xFFu));
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
        std::uint32_t val = 0;
        for (int i = 0; i < 4; ++i)
            val = (val << 8) | static_cast<std::uint32_t>(buffer[offset++]);
        return static_cast<std::int32_t>(val);
    };

    auto readInt64 = [&]() -> std::int64_t {
        if (offset + 8 > buffer.size()) throw std::runtime_error("Underflow int64");
        std::uint64_t val = 0;
        for (int i = 0; i < 8; ++i)
            val = (val << 8) | static_cast<std::uint64_t>(buffer[offset++]);
        return static_cast<std::int64_t>(val);
    };

    // A count read from an untrusted buffer decides how much this function
    // allocates and how many times it loops, so it is range-checked before use.
    auto readCount = [&](const char* what) -> std::size_t {
        const std::int32_t n = readInt32();
        if (n < 0 || static_cast<std::size_t>(n) > MAX_ELEMENT_COUNT)
            throw std::runtime_error(std::string("Implausible ") + what + " count");
        return static_cast<std::size_t>(n);
    };

    CodaTimeFrame event;
    const std::size_t tfCount = readCount("time frame");

    for (std::size_t t = 0; t < tfCount; ++t) {
        TimeFrame tf;
        const std::size_t rocCount = readCount("ROC");

        for (std::size_t r = 0; r < rocCount; ++r) {
            RocTimeFrameBank roc;
            roc.rocId = readInt32();
            roc.frameNumber = readInt32();
            roc.timeStamp = readInt64();

            const std::size_t hitCount = readCount("hit");
            std::vector<std::int32_t> crates(hitCount), slots(hitCount), chans(hitCount), charges(hitCount);
            std::vector<std::int64_t> times(hitCount);

            for (std::size_t i = 0; i < hitCount; ++i) crates[i] = readInt32();
            for (std::size_t i = 0; i < hitCount; ++i) slots[i] = readInt32();
            for (std::size_t i = 0; i < hitCount; ++i) chans[i] = readInt32();
            for (std::size_t i = 0; i < hitCount; ++i) charges[i] = readInt32();
            for (std::size_t i = 0; i < hitCount; ++i) times[i] = readInt64();

            for (std::size_t i = 0; i < hitCount; ++i)
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
