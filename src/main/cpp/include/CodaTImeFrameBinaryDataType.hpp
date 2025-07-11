#ifndef CODA_TIME_FRAME_BINARY_DATA_TYPE_HPP
#define CODA_TIME_FRAME_BINARY_DATA_TYPE_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <algorithm>
#include <memory>
#include <ersap/any.hpp>
#include <ersap/engine_data_type.hpp>
#include <ersap/serializer.hpp>

namespace ersap {
namespace coda {

struct FADCHit {
    std::int32_t crate;
    std::int32_t slot;
    std::int32_t channel;
    std::int32_t charge;
    std::int64_t time;

    FADCHit() = default;

    FADCHit(std::int32_t c, std::int32_t s, std::int32_t ch,
            std::int32_t charge, std::int64_t t)
        : crate(c), slot(s), channel(ch), charge(charge), time(t) {}

    std::string getName() const {
        return std::to_string(crate) + "-" + std::to_string(slot) + "-" + std::to_string(channel);
    }

    std::int32_t getId() const {
        return (crate * 1000) + (slot * 16) + channel;
    }
};

struct RocTimeFrameBank {
    std::int32_t rocId;
    std::int32_t frameNumber;
    std::int64_t timeStamp;
    std::vector<FADCHit> hits;

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

using TimeFrame = std::vector<RocTimeFrameBank>;

struct CodaTimeFrame {
    std::vector<TimeFrame> timeFrames;
    std::int64_t eventId = 0;
    std::int64_t creationTime = 0;
    std::string sourceInfo;

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

    std::size_t getTimeFrameCount() const {
        return timeFrames.size();
    }

    std::size_t getTotalRocCount() const {
        std::size_t count = 0;
        for (const auto& tf : timeFrames) {
            count += tf.size();
        }
        return count;
    }

    std::size_t getTotalHitCount() const {
        std::size_t count = 0;
        for (const auto& tf : timeFrames) {
            for (const auto& roc : tf) {
                count += roc.hits.size();
            }
        }
        return count;
    }

    std::vector<FADCHit> getAllHits() const {
        std::vector<FADCHit> all;
        for (const auto& tf : timeFrames) {
            for (const auto& roc : tf) {
                all.insert(all.end(), roc.hits.begin(), roc.hits.end());
            }
        }
        return all;
    }

    std::vector<RocTimeFrameBank> getAllRocBanks() const {
        std::vector<RocTimeFrameBank> all;
        for (const auto& tf : timeFrames) {
            all.insert(all.end(), tf.begin(), tf.end());
        }
        return all;
    }

    bool isEmpty() const {
        return timeFrames.empty() ||
               std::all_of(timeFrames.begin(), timeFrames.end(),
                          [](const TimeFrame& tf) { return tf.empty(); });
    }

    bool isValid() const {
        for (const auto& tf : timeFrames) {
            for (const auto& roc : tf) {
                if (roc.frameNumber < 0 || roc.timeStamp < 0) return false;
            }
        }
        return true;
    }
};

class CodaTimeFrameSerializer : public ersap::Serializer {
public:
    std::vector<std::uint8_t> write(const ersap::any& data) const override;
    ersap::any read(const std::vector<std::uint8_t>& buffer) const override;
    
    // Public static methods for binary serialization
    static std::vector<std::uint8_t> serializeToBinary(const CodaTimeFrame& event);
    static CodaTimeFrame deserializeFromBinary(const std::vector<std::uint8_t>& buffer);
};

const std::string CODA_TIME_FRAME_MIME_TYPE = "binary/coda-time-frame";

const ersap::EngineDataType CODA_TIME_FRAME_TYPE{
    CODA_TIME_FRAME_MIME_TYPE,
    std::make_unique<CodaTimeFrameSerializer>()
};

} // namespace coda
} // namespace ersap

#endif // CODA_TIME_FRAME_BINARY_DATA_TYPE_HPP
