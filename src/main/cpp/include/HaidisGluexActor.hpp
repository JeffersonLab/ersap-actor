/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * ERSAP actor that interfaces with ET system to process GluEx physics event data.
 * Receives SINT32 trigger from Java source, reads events from ET system,
 * and processes four-vector physics data with kinematic fit results for eta meson analysis.
 *
 * @author gurjyan
 * @project ersap-actor
 */

#ifndef HAIDIS_GLUEX_ACTOR_HPP
#define HAIDIS_GLUEX_ACTOR_HPP

#include <ersap/engine.hpp>
#include <ersap/engine_data.hpp>
#include <ersap/engine_data_type.hpp>
#include <et.h>
#include <string>
#include <memory>
#include <set>
#include <vector>
#include <cmath>

namespace ersap {
namespace coda {

/**
 * Simple Lorentz four-vector for physics calculations.
 * Stores (E, Px, Py, Pz) with particle mass.
 */
struct LorentzVector {
    double E, Px, Py, Pz;
    double mass;

    LorentzVector(double e = 0.0, double px = 0.0, double py = 0.0, double pz = 0.0, double m = 0.0)
        : E(e), Px(px), Py(py), Pz(pz), mass(m) {}

    // Vector addition
    LorentzVector operator+(const LorentzVector& other) const {
        return LorentzVector(E + other.E, Px + other.Px, Py + other.Py, Pz + other.Pz, 0.0);
    }

    // Invariant mass squared: M^2 = E^2 - P^2
    double M2() const {
        return E*E - (Px*Px + Py*Py + Pz*Pz);
    }

    // Invariant mass: M = sqrt(max(M^2, 0))
    double M() const {
        double m2 = M2();
        return m2 >= 0.0 ? std::sqrt(m2) : 0.0;
    }
};

/**
 * GluEx event data structure.
 * Contains four-vectors for reconstructed particles plus kinematic fit results.
 */
struct EventData {
    LorentzVector pip;     // π+ four-vector
    LorentzVector pim;     // π- four-vector
    LorentzVector g1;      // γ1 four-vector
    LorentzVector g2;      // γ2 four-vector
    double imass_kfit;     // Invariant mass from kinematic fit
    double imassGG_kfit;   // γγ invariant mass from kinematic fit
    double kfit_prob;      // Kinematic fit probability/confidence
};

/**
 * Results from GluEx Dalitz analysis.
 * X and Y are Dalitz plot coordinates for eta -> π+π-π0 decay.
 */
struct GluexAnalysisResult {
    double X;                   // Dalitz X coordinate
    double Y;                   // Dalitz Y coordinate
    bool pass_event_selection;  // Whether event passes quality cuts
};

/**
 * ERSAP engine that bridges Java trigger signals to ET system event processing
 * for GluEx eta meson Dalitz analysis.
 *
 * Architecture:
 * 1. Java UniAdapterSourceEngine sends SINT32 trigger value
 * 2. HaidisGluexActor validates trigger and reads next event from ET system
 * 3. Processes physics data (19 doubles: 16 for four-vectors + 3 kfit scalars)
 * 4. Performs eta meson Dalitz analysis with event selection
 * 5. Returns event to ET system and outputs Dalitz coordinates for passing events
 *
 * The SINT32 input serves solely as a trigger signal and carries no semantic data.
 */
class HaidisGluexActor : public ersap::Engine {
public:
    HaidisGluexActor() = default;
    virtual ~HaidisGluexActor();

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
    // GluEx physics constants (GeV)
    static constexpr double M_ETA  = 0.547862;  // η meson mass
    static constexpr double M_PIPM = 0.139570;  // π± mass
    static constexpr double M_PI0  = 0.134977;  // π0 mass

    // Configuration parameters
    bool verbose_ = false;
    std::string etFilename_;
    std::string etHost_ = "localhost";
    int etPort_ = ET_SERVER_PORT;
    std::string stationName_ = "ERSAP_GLUEX_PROCESSOR";

    // ET system handles
    et_sys_id etSys_ = nullptr;
    et_att_id etAtt_ = 0;
    et_stat_id etStat_ = 0;
    bool etConnected_ = false;

    // Statistics
    std::size_t eventCount_ = 0;
    std::size_t errorCount_ = 0;
    std::size_t passedEventCount_ = 0;

    // Expected physics data size: 19 doubles (16 for four-vectors + 3 kfit scalars)
    static constexpr std::size_t EXPECTED_SIZE = 19 * sizeof(double);

    // Helper methods for ET system management
    bool initializeET();
    void cleanupET();

    // Helper methods for data processing and output
    void printEventData(const EventData& ev) const;
    void printEventSummary() const;
    void printSeparator(const std::string& title = "") const;

    // GluEx physics analysis method (extracted from factored_gluex_analysis.C)
    GluexAnalysisResult analysis(const EventData& ev) const;
};

} // namespace coda
} // namespace ersap

// C interface for engine creation (required by ERSAP framework)
extern "C" std::unique_ptr<ersap::Engine> create_engine();

#endif // HAIDIS_GLUEX_ACTOR_HPP
