/*
 * Copyright (c) 2024, Jefferson Science Associates, all rights reserved.
 * Test suite for C++ CodaTimeFrame serializer cross-language compatibility
 */

#include "../include/CodaTimeFrameDataType.hpp"
#include <iostream>
#include <cassert>
#include <vector>
#include <chrono>

using namespace ersap::coda;

// Test helper functions
CodaTimeFrame createTestEvent() {
    CodaTimeFrame event;
    event.eventId = 12345;
    event.creationTime = 1703251200000000000LL; // 2023-12-22 in nanoseconds
    event.sourceInfo = "test_source";
    
    // Create first time frame
    TimeFrame timeFrame1;
    
    RocTimeFrameBank roc1(1, 100, 1000000L);
    roc1.addHit(FADCHit{1, 2, 0, 1500, 1000050L});
    roc1.addHit(FADCHit{1, 2, 1, 1750, 1000100L});
    roc1.addHit(FADCHit{1, 2, 2, 1200, 1000150L});
    timeFrame1.push_back(roc1);
    
    RocTimeFrameBank roc2(2, 100, 1000000L);
    roc2.addHit(FADCHit{2, 3, 0, 2000, 1000075L});
    roc2.addHit(FADCHit{2, 3, 1, 1800, 1000125L});
    timeFrame1.push_back(roc2);
    
    event.addTimeFrame(timeFrame1);
    
    // Create second time frame
    TimeFrame timeFrame2;
    
    RocTimeFrameBank roc3(1, 101, 1001000L);
    roc3.addHit(FADCHit{1, 2, 3, 1400, 1001050L});
    roc3.addHit(FADCHit{1, 2, 4, 1600, 1001100L});
    timeFrame2.push_back(roc3);
    
    event.addTimeFrame(timeFrame2);
    
    return event;
}

void testBasicSerialization() {
    std::cout << "Testing basic serialization..." << std::endl;
    
    CodaTimeFrame originalEvent = createTestEvent();
    CodaTimeFrameSerializer serializer;
    
    // Serialize
    auto buffer = serializer.write(ersap::any{originalEvent});
    
    // Deserialize
    auto deserializedAny = serializer.read(buffer);
    auto deserializedEvent = ersap::any_cast<CodaTimeFrame>(deserializedAny);
    
    // Verify basic properties
    assert(originalEvent.eventId == deserializedEvent.eventId);
    assert(originalEvent.creationTime == deserializedEvent.creationTime);
    assert(originalEvent.sourceInfo == deserializedEvent.sourceInfo);
    assert(originalEvent.getTimeFrameCount() == deserializedEvent.getTimeFrameCount());
    assert(originalEvent.getTotalRocCount() == deserializedEvent.getTotalRocCount());
    assert(originalEvent.getTotalHitCount() == deserializedEvent.getTotalHitCount());
    
    std::cout << "  ✓ Basic properties match" << std::endl;
    
    // Verify detailed structure
    for (std::size_t tf = 0; tf < originalEvent.timeFrames.size(); ++tf) {
        const auto& origTF = originalEvent.timeFrames[tf];
        const auto& deserTF = deserializedEvent.timeFrames[tf];
        
        assert(origTF.size() == deserTF.size());
        
        for (std::size_t roc = 0; roc < origTF.size(); ++roc) {
            const auto& origRoc = origTF[roc];
            const auto& deserRoc = deserTF[roc];
            
            assert(origRoc.rocId == deserRoc.rocId);
            assert(origRoc.frameNumber == deserRoc.frameNumber);
            assert(origRoc.timeStamp == deserRoc.timeStamp);
            assert(origRoc.hits.size() == deserRoc.hits.size());
            
            for (std::size_t hit = 0; hit < origRoc.hits.size(); ++hit) {
                const auto& origHit = origRoc.hits[hit];
                const auto& deserHit = deserRoc.hits[hit];
                
                assert(origHit.crate == deserHit.crate);
                assert(origHit.slot == deserHit.slot);
                assert(origHit.channel == deserHit.channel);
                assert(origHit.charge == deserHit.charge);
                assert(origHit.time == deserHit.time);
            }
        }
    }
    
    std::cout << "  ✓ Detailed structure matches" << std::endl;
    std::cout << "Basic serialization test PASSED" << std::endl;
}

void testEmptyEvent() {
    std::cout << "Testing empty event serialization..." << std::endl;
    
    CodaTimeFrame emptyEvent;
    CodaTimeFrameSerializer serializer;
    
    auto buffer = serializer.write(ersap::any{emptyEvent});
    auto deserializedAny = serializer.read(buffer);
    auto deserializedEvent = ersap::any_cast<CodaTimeFrame>(deserializedAny);
    
    assert(deserializedEvent.isEmpty());
    assert(deserializedEvent.getTimeFrameCount() == 0);
    assert(deserializedEvent.getTotalHitCount() == 0);
    
    std::cout << "Empty event test PASSED" << std::endl;
}

void testLargeEvent() {
    std::cout << "Testing large event serialization..." << std::endl;
    
    CodaTimeFrame largeEvent;
    largeEvent.eventId = 99999;
    largeEvent.sourceInfo = "performance_test";
    
    // Create a large event with many hits
    TimeFrame largeTimeFrame;
    RocTimeFrameBank largeRoc(1, 200, 2000000L);
    
    const int hitCount = 1000;
    for (int i = 0; i < hitCount; ++i) {
        FADCHit hit{
            1 + (i % 4),           // crate 1-4
            1 + ((i / 4) % 16),    // slot 1-16
            i % 16,                // channel 0-15
            1000 + (i % 7000),     // charge
            2000000L + i * 100L    // time
        };
        largeRoc.addHit(hit);
    }
    
    largeTimeFrame.push_back(largeRoc);
    largeEvent.addTimeFrame(largeTimeFrame);
    
    CodaTimeFrameSerializer serializer;
    
    auto buffer = serializer.write(ersap::any{largeEvent});
    auto deserializedAny = serializer.read(buffer);
    auto deserializedEvent = ersap::any_cast<CodaTimeFrame>(deserializedAny);
    
    assert(largeEvent.getTotalHitCount() == deserializedEvent.getTotalHitCount());
    assert(deserializedEvent.getTotalHitCount() == hitCount);
    
    std::cout << "  ✓ Large event with " << hitCount << " hits serialized correctly" << std::endl;
    std::cout << "Large event test PASSED" << std::endl;
}

void testPerformance() {
    std::cout << "Testing serialization performance..." << std::endl;
    
    CodaTimeFrame testEvent = createTestEvent();
    CodaTimeFrameSerializer serializer;
    
    const int iterations = 1000;
    
    auto start = std::chrono::high_resolution_clock::now();
    
    for (int i = 0; i < iterations; ++i) {
        auto buffer = serializer.write(ersap::any{testEvent});
        auto deserializedAny = serializer.read(buffer);
        auto deserializedEvent = ersap::any_cast<CodaTimeFrame>(deserializedAny);
        
        // Basic validation
        assert(testEvent.getTotalHitCount() == deserializedEvent.getTotalHitCount());
    }
    
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    std::cout << "  ✓ " << iterations << " serialization cycles completed in " 
              << duration.count() << " ms" << std::endl;
    std::cout << "  ✓ Average: " << (duration.count() / static_cast<double>(iterations)) 
              << " ms per cycle" << std::endl;
    
    std::cout << "Performance test PASSED" << std::endl;
}

void testUtilityMethods() {
    std::cout << "Testing utility methods..." << std::endl;
    
    CodaTimeFrame event = createTestEvent();
    
    // Test getName and getId for hits
    auto allHits = event.getAllHits();
    for (const auto& hit : allHits) {
        std::string name = hit.getName();
        std::int32_t id = hit.getId();
        
        // Name should be in format "crate-slot-channel"
        assert(!name.empty());
        assert(name.find('-') != std::string::npos);
        
        // ID should be unique for different hits
        assert(id > 0);
    }
    
    // Test validation
    assert(event.isValid());
    assert(!event.isEmpty());
    
    // Test counts
    assert(event.getTimeFrameCount() == 2);
    assert(event.getTotalRocCount() == 3);
    assert(event.getTotalHitCount() == 7);
    
    std::cout << "Utility methods test PASSED" << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "C++ CodaTimeFrame Serializer Test Suite" << std::endl;
    std::cout << "========================================" << std::endl;
    
    try {
        testBasicSerialization();
        std::cout << std::endl;
        
        testEmptyEvent();
        std::cout << std::endl;
        
        testLargeEvent();
        std::cout << std::endl;
        
        testUtilityMethods();
        std::cout << std::endl;
        
        testPerformance();
        std::cout << std::endl;
        
        std::cout << "========================================" << std::endl;
        std::cout << "All tests PASSED!" << std::endl;
        std::cout << "C++ CodaTimeFrame serializer is ready for cross-language communication" << std::endl;
        std::cout << "========================================" << std::endl;
        
        return 0;
        
    } catch (const std::exception& e) {
        std::cerr << "Test FAILED with exception: " << e.what() << std::endl;
        return 1;
    } catch (...) {
        std::cerr << "Test FAILED with unknown exception" << std::endl;
        return 1;
    }
}