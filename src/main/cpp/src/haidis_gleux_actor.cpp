/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * Implementation of HAIDIS GluEx actor for ET system event processing
 * @author gurjyan
 * @project ersap-actor
 */

#include "HaidisGluexActor.hpp"
#include <ersap/stdlib/json_utils.hpp>
#include <iostream>
#include <iomanip>
#include <sstream>
#include <cstring>
#include <cmath>

// C interface for engine creation
extern "C"
std::unique_ptr<ersap::Engine> create_engine()
{
    return std::make_unique<ersap::coda::HaidisGluexActor>();
}

namespace ersap {
namespace coda {

HaidisGluexActor::~HaidisGluexActor() {
    cleanupET();
}

ersap::EngineData HaidisGluexActor::configure(ersap::EngineData& input) {
    auto output = ersap::EngineData{};

    // Parse JSON configuration if provided
    if (input.mime_type() == ersap::type::JSON.mime_type()) {
        try {
            auto config = ersap::stdlib::parse_json(input);

            // Parse ET configuration parameters
            if (!config["et_filename"].is_null()) {
                etFilename_ = config["et_filename"].string_value();
            }
            if (!config["et_host"].is_null()) {
                etHost_ = config["et_host"].string_value();
            }
            if (!config["et_port"].is_null()) {
                etPort_ = config["et_port"].int_value();
            }
            if (!config["station_name"].is_null()) {
                stationName_ = config["station_name"].string_value();
            }
            if (!config["verbose"].is_null()) {
                verbose_ = config["verbose"].bool_value();
            }

            if (verbose_) {
                std::cout << "HaidisGluexActor configuration:" << std::endl;
                std::cout << "  - ET filename: " << etFilename_ << std::endl;
                std::cout << "  - ET host: " << etHost_ << std::endl;
                std::cout << "  - ET port: " << etPort_ << std::endl;
                std::cout << "  - Station name: " << stationName_ << std::endl;
                std::cout << "  - Verbose: " << verbose_ << std::endl;
            }

        } catch (const std::exception& e) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Error parsing configuration: " + std::string(e.what()));
            std::cerr << "Error parsing HaidisGluexActor configuration: " << e.what() << std::endl;
            return output;
        }
    }

    // Validate required configuration
    if (etFilename_.empty()) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("ET filename is required in configuration");
        std::cerr << "Error: ET filename not specified in configuration" << std::endl;
        return output;
    }

    // Initialize ET system connection
    if (!initializeET()) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Failed to initialize ET system connection");
        std::cerr << "Error: Failed to connect to ET system" << std::endl;
        return output;
    }

    if (verbose_) {
        std::cout << "HaidisGluexActor configured successfully and connected to ET system" << std::endl;
    }

    return output;
}

ersap::EngineData HaidisGluexActor::execute(ersap::EngineData& input) {
    auto output = ersap::EngineData{};

    // Increment execute call counter
    executeCallCount_++;

    // Debug: Log execute() call information
    if (verbose_) {
        std::cout << "\n========================================" << std::endl;
        std::cout << "DEBUG: execute() called - Call #" << executeCallCount_ << std::endl;
        std::cout << "  Input MIME type: " << input.mime_type() << std::endl;
        std::cout << "  ET connected: " << (etConnected_ ? "YES" : "NO") << std::endl;
        std::cout << "========================================\n" << std::endl;
    }

    // Verify input data type - expecting SINT32 trigger
    if (input.mime_type() != ersap::type::SINT32.mime_type()) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Wrong input type: expected SINT32 trigger, got " + input.mime_type());
        std::cerr << "Error: Expected SINT32 input type, got " << input.mime_type() << std::endl;
        errorCount_++;
        return output;
    }

    // Check ET connection status
    if (!etConnected_) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("ET system not connected. Configure actor first.");
        std::cerr << "Error: ET system not connected" << std::endl;
        errorCount_++;
        return output;
    }

    // Note: SINT32 value is just a trigger - we don't need to extract or use it
    // It signals us to read the next event from ET

    try {
        et_event* pe;

        // Get event from ET system (blocking call)
        int status = et_event_get(etSys_, etAtt_, &pe, ET_SLEEP, NULL);

        if (status == ET_ERROR_DEAD) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("ET system is dead");
            std::cerr << "Error: ET system is dead" << std::endl;
            errorCount_++;
            return output;
        } else if (status == ET_ERROR_WAKEUP) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("ET event_get woken up");
            std::cerr << "Warning: ET event_get woken up" << std::endl;
            return output;
        } else if (status != ET_OK) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Failed to get event from ET (status " + std::to_string(status) + ")");
            std::cerr << "Error: Failed to get event from ET (status " << status << ")" << std::endl;
            errorCount_++;
            return output;
        }

        // Get event data and length
        void* data_ptr;
        size_t data_len;
        et_event_getdata(pe, &data_ptr);
        et_event_getlength(pe, &data_len);

        // Debug: Log ET event metadata
        if (verbose_) {
            std::cout << "DEBUG: ET event received" << std::endl;
            std::cout << "  ET event data size: " << data_len << " bytes" << std::endl;
        }

        // Validate payload size - expecting 20 doubles per event (16 four-vector components + 3 kfit scalars + 1 data_id)
        constexpr size_t DOUBLES_PER_EVENT = 20;
        constexpr size_t BYTES_PER_EVENT = DOUBLES_PER_EVENT * sizeof(double);

        // Debug: Log calculated event count
        if (verbose_) {
            size_t calculated_events = data_len / BYTES_PER_EVENT;
            size_t leftover = data_len % BYTES_PER_EVENT;
            std::cout << "  Expected bytes per physics event: " << BYTES_PER_EVENT << std::endl;
            std::cout << "  Calculated physics events in ET event: " << calculated_events << std::endl;
            if (leftover > 0) {
                std::cout << "  WARNING: " << leftover << " leftover bytes" << std::endl;
            }
            std::cout << std::endl;
        }

        if (data_len < BYTES_PER_EVENT) {
            std::cerr << "Warning: Received " << data_len
                      << " bytes, expected minimum " << BYTES_PER_EVENT << " bytes" << std::endl;

            // Return event to ET system
            status = et_event_put(etSys_, etAtt_, pe);
            if (status != ET_OK) {
                std::cerr << "Error: Failed to return event to ET (status " << status << ")" << std::endl;
            }

            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Invalid ET event size: " + std::to_string(data_len) +
                                   " bytes, expected minimum " + std::to_string(BYTES_PER_EVENT));
            errorCount_++;
            return output;
        }

        // Calculate number of complete events in the payload
        size_t num_events = data_len / BYTES_PER_EVENT;
        size_t leftover_bytes = data_len % BYTES_PER_EVENT;

        if (leftover_bytes > 0 && verbose_) {
            std::cout << "Warning: Payload has " << leftover_bytes
                      << " leftover bytes (not a complete event). Processing "
                      << num_events << " complete events only." << std::endl;
        }

        // Interpret data as array of doubles
        double* doubles = static_cast<double*>(data_ptr);

        // Vector to collect analysis results (2 doubles per passing event: X, Y)
        std::vector<double> analysis_results;
        double data_id = 0.0;

        // Process each event (20 doubles each)
        for (size_t event_idx = 0; event_idx < num_events; ++event_idx) {
            const double* event_data = doubles + (event_idx * DOUBLES_PER_EVENT);

            // Extract event data from payload
            // Layout: [pip E px py pz][pim E px py pz][g1 E px py pz][g2 E px py pz]
            //         [imass_kfit][imassGG_kfit][kfit_prob]
            EventData ev;
            ev.pip.E  = event_data[0];
            ev.pip.Px = event_data[1];
            ev.pip.Py = event_data[2];
            ev.pip.Pz = event_data[3];
            ev.pip.mass = M_PIPM;

            ev.pim.E  = event_data[4];
            ev.pim.Px = event_data[5];
            ev.pim.Py = event_data[6];
            ev.pim.Pz = event_data[7];
            ev.pim.mass = M_PIPM;

            ev.g1.E  = event_data[8];
            ev.g1.Px = event_data[9];
            ev.g1.Py = event_data[10];
            ev.g1.Pz = event_data[11];
            ev.g1.mass = 0.0;  // photon

            ev.g2.E  = event_data[12];
            ev.g2.Px = event_data[13];
            ev.g2.Py = event_data[14];
            ev.g2.Pz = event_data[15];
            ev.g2.mass = 0.0;  // photon

            ev.imass_kfit   = event_data[16];
            ev.imassGG_kfit = event_data[17];
            ev.kfit_prob    = event_data[18];
            ev.data_id      = event_data[19];
            data_id         = ev.data_id;

            // Print event data if verbose
            if (verbose_) {
                if (num_events > 1) {
                    printSeparator("GluEx Physics Event Data - Event " + std::to_string(event_idx + 1) +
                                   "/" + std::to_string(num_events));
                } else {
                    printSeparator("GluEx Physics Event Data");
                }
                printEventData(ev);
                printSeparator();
            }

            // Run GluEx Dalitz analysis on this event (extracted from factored_gluex_analysis.C)
            GluexAnalysisResult result = analysis(ev);

            // Only collect results that pass event selection cuts
            if (result.pass_event_selection) {
                analysis_results.push_back(result.X);
                analysis_results.push_back(result.Y);
                passedEventCount_++;

                if (verbose_) {
                    std::cout << "Event " << (event_idx + 1) << " PASSED event selection:" << std::endl;
                    std::cout << "  X = " << result.X << std::endl;
                    std::cout << "  Y = " << result.Y << std::endl;
                }
            } else if (verbose_) {
                std::cout << "Event " << (event_idx + 1) << " FAILED event selection (not included in output)" << std::endl;
            }
        }

        if (verbose_ && num_events > 1) {
            std::cout << "Processed " << num_events << " events, "
                      << (analysis_results.size() / 2) << " passed event selection" << std::endl;
        }

        // Return event to ET system
        if (verbose_) {
            std::cout << "\nDEBUG: Returning ET event to system..." << std::endl;
        }
        status = et_event_put(etSys_, etAtt_, pe);
        if (verbose_) {
            std::cout << "  et_event_put status: " << status;
            if (status == ET_OK) {
                std::cout << " (ET_OK - SUCCESS)";
            } else if (status == ET_ERROR_DEAD) {
                std::cout << " (ET_ERROR_DEAD - SYSTEM DEAD)";
            } else {
                std::cout << " (ERROR)";
            }
            std::cout << std::endl;
        }
        if (status == ET_ERROR_DEAD) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("ET system is dead (during event_put)");
            std::cerr << "Error: ET system is dead" << std::endl;
            errorCount_++;
            return output;
        } else if (status != ET_OK) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Failed to return event to ET (status " + std::to_string(status) + ")");
            std::cerr << "Error: Failed to return event to ET (status " << status << ")" << std::endl;
            errorCount_++;
            return output;
        }

        // Update statistics
        eventCount_++;

        if (verbose_ && eventCount_ % 100 == 0) {
            printEventSummary();
        }

        // Prepend data_id to analysis results before output.
        // Output layout: [data_id][X0][Y0][X1][Y1]...
        std::vector<double> output_results;
        output_results.reserve(analysis_results.size() + 1);
        output_results.push_back(data_id);
        output_results.insert(output_results.end(),
                              analysis_results.begin(),
                              analysis_results.end());

        output.set_data(ersap::type::ARRAY_DOUBLE, output_results);

        // Debug: Log output summary
        if (verbose_) {
            std::cout << "\nDEBUG: Returning from execute() - Call #" << executeCallCount_ << std::endl;
            std::cout << "  Output data type: ARRAY_DOUBLE" << std::endl;
            std::cout << "  data_id: " << data_id << std::endl;
            std::cout << "  Output array size: " << output_results.size() << " doubles" << std::endl;
            std::cout << "  Passing events: " << (analysis_results.size() / 2) << std::endl;
            std::cout << "  Total execute calls so far: " << executeCallCount_ << std::endl;
            std::cout << "  Total physics events processed: " << eventCount_ << std::endl;
            std::cout << "  Total passed events: " << passedEventCount_ << std::endl;
            std::cout << "========================================\n" << std::endl;
        }

    } catch (const std::exception& e) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Error processing ET event: " + std::string(e.what()));
        std::cerr << "Error in HaidisGluexActor: " << e.what() << std::endl;
        errorCount_++;
    }

    return output;
}

ersap::EngineData HaidisGluexActor::execute_group(const std::vector<ersap::EngineData>&) {
    // Group processing not implemented for this actor
    return {};
}

std::vector<ersap::EngineDataType> HaidisGluexActor::input_data_types() const {
    return { ersap::type::SINT32, ersap::type::JSON };
}

std::vector<ersap::EngineDataType> HaidisGluexActor::output_data_types() const {
    return { ersap::type::ARRAY_DOUBLE, ersap::type::JSON };
}

std::set<std::string> HaidisGluexActor::states() const {
    return {}; // Stateless engine
}

std::string HaidisGluexActor::name() const {
    return "HaidisGluexActor";
}

std::string HaidisGluexActor::author() const {
    return "Jefferson Lab";
}

std::string HaidisGluexActor::description() const {
    return "ERSAP actor interfacing with ET system to process GluEx physics four-vector data "
           "with kinematic fit results. Receives SINT32 trigger from Java source, reads events "
           "from ET, and performs eta meson Dalitz analysis (η → π+π-π0) with event selection cuts.";
}

std::string HaidisGluexActor::version() const {
    return "1.0.0";
}

// Private helper methods

bool HaidisGluexActor::initializeET() {
    if (etConnected_) {
        std::cerr << "Warning: ET already connected, skipping initialization" << std::endl;
        return true;
    }

    et_openconfig openconfig;
    et_statconfig statconfig;
    int status;

    // Initialize ET open configuration
    status = et_open_config_init(&openconfig);
    if (status != ET_OK) {
        std::cerr << "Error: Failed to initialize ET open config" << std::endl;
        return false;
    }

    et_open_config_setwait(openconfig, ET_OPEN_WAIT);
    et_open_config_sethost(openconfig, etHost_.c_str());
    et_open_config_setcast(openconfig, ET_DIRECT);
    et_open_config_setserverport(openconfig, etPort_);

    struct timespec timeout;
    timeout.tv_sec = 10;
    timeout.tv_nsec = 0;
    et_open_config_settimeout(openconfig, timeout);

    // Open ET system
    if (verbose_) {
        std::cout << "Opening ET system: " << etFilename_
                  << " at " << etHost_ << ":" << etPort_ << std::endl;
    }

    status = et_open(&etSys_, etFilename_.c_str(), openconfig);
    et_open_config_destroy(openconfig);

    if (status != ET_OK) {
        std::cerr << "Error: Failed to open ET system (status " << status << ")" << std::endl;
        return false;
    }

    if (verbose_) {
        std::cout << "ET system opened successfully" << std::endl;
    }

    // Initialize station configuration
    status = et_station_config_init(&statconfig);
    if (status != ET_OK) {
        std::cerr << "Error: Failed to initialize station config" << std::endl;
        et_close(etSys_);
        return false;
    }

    // Configure station
    et_station_config_setuser(statconfig, ET_STATION_USER_MULTI);
    et_station_config_setrestore(statconfig, ET_STATION_RESTORE_IN);
    et_station_config_setprescale(statconfig, 1);
    et_station_config_setcue(statconfig, 1);
    et_station_config_setselect(statconfig, ET_STATION_SELECT_ALL);
    et_station_config_setblock(statconfig, ET_STATION_BLOCKING);

    // Create station or attach if it already exists
    if (verbose_) {
        std::cout << "Creating/attaching to station: " << stationName_ << std::endl;
    }

    status = et_station_create_at(etSys_, &etStat_, stationName_.c_str(),
                                   statconfig, ET_END, 0);

    if (status == ET_ERROR_EXISTS) {
        // Station already exists, get its ID
        status = et_station_name_to_id(etSys_, &etStat_, stationName_.c_str());
        if (status != ET_OK) {
            std::cerr << "Error: Station exists but cannot get ID (status "
                      << status << ")" << std::endl;
            et_station_config_destroy(statconfig);
            et_close(etSys_);
            return false;
        }
        if (verbose_) {
            std::cout << "Station already exists, using existing station" << std::endl;
        }
    } else if (status != ET_OK) {
        std::cerr << "Error: Failed to create station (status " << status << ")" << std::endl;
        et_station_config_destroy(statconfig);
        et_close(etSys_);
        return false;
    } else {
        if (verbose_) {
            std::cout << "Station created successfully" << std::endl;
        }
    }

    et_station_config_destroy(statconfig);

    // Attach to the station
    status = et_station_attach(etSys_, etStat_, &etAtt_);
    if (status != ET_OK) {
        std::cerr << "Error: Failed to attach to station (status " << status << ")" << std::endl;
        et_close(etSys_);
        return false;
    }

    if (verbose_) {
        std::cout << "Attached to station successfully" << std::endl;
        std::cout << "Ready to process ET events" << std::endl;
    }

    etConnected_ = true;
    return true;
}

void HaidisGluexActor::cleanupET() {
    if (!etConnected_) {
        return;
    }

    if (verbose_) {
        std::cout << "Cleaning up ET system connection..." << std::endl;
    }

    int status;

    if (etAtt_ != 0) {
        status = et_station_detach(etSys_, etAtt_);
        if (status != ET_OK) {
            std::cerr << "Warning: Failed to detach from station (status "
                      << status << ")" << std::endl;
        }
        etAtt_ = 0;
    }

    if (etSys_ != nullptr) {
        status = et_close(etSys_);
        if (status != ET_OK) {
            std::cerr << "Warning: Failed to close ET system (status "
                      << status << ")" << std::endl;
        }
        etSys_ = nullptr;
    }

    etStat_ = 0;
    etConnected_ = false;

    if (verbose_) {
        std::cout << "ET cleanup complete" << std::endl;
    }
}

// GluEx Dalitz analysis method - extracted from factored_gluex_analysis.C (lines 52-70)
GluexAnalysisResult HaidisGluexActor::analysis(const EventData& ev) const {
    // Reconstruct π0 from two photons (same as in factored_gluex_analysis.C line 54)
    LorentzVector pi0 = ev.g1 + ev.g2;

    // Compute invariant mass squared for particle pairs (lines 55-57)
    double s_pimpi0 = (ev.pim + pi0).M2();
    double s_pippi0 = (ev.pip + pi0).M2();
    double s_pippim = (ev.pip + ev.pim).M2();

    // Compute Dalitz coordinates using eta meson formulas (lines 59-65)
    // Q is the kinematic range available for the decay
    double Q = M_ETA - 2*M_PIPM - M_PI0;
    // s_centre is the center point of the Dalitz plot
    double s_centre = (M_ETA*M_ETA + 2*M_PIPM*M_PIPM + M_PI0*M_PI0) / 3.0;
    // denom is the normalization factor
    double denom = Q * (Q + 3*M_PI0);

    GluexAnalysisResult res;
    // X coordinate: measures asymmetry between π-π0 and π+π0
    res.X = std::sqrt(3.0) * (s_pimpi0 - s_pippi0) / denom;
    // Y coordinate: measures deviation of π+π- system from center
    res.Y = 3.0 * (s_pippim - s_centre) / denom;

    // Apply event selection cuts based on kinematic fit quality (lines 66-68)
    res.pass_event_selection = (ev.kfit_prob    >  0.0001 &&
                                ev.imass_kfit   >= 0.45   && ev.imass_kfit   < 0.58 &&
                                ev.imassGG_kfit >  0.1    && ev.imassGG_kfit < 0.15);

    return res;
}

void HaidisGluexActor::printEventData(const EventData& ev) const {
    std::cout << "π+ four-vector: E=" << ev.pip.E
              << ", Px=" << ev.pip.Px
              << ", Py=" << ev.pip.Py
              << ", Pz=" << ev.pip.Pz << std::endl;

    std::cout << "π- four-vector: E=" << ev.pim.E
              << ", Px=" << ev.pim.Px
              << ", Py=" << ev.pim.Py
              << ", Pz=" << ev.pim.Pz << std::endl;

    std::cout << "γ1 four-vector: E=" << ev.g1.E
              << ", Px=" << ev.g1.Px
              << ", Py=" << ev.g1.Py
              << ", Pz=" << ev.g1.Pz << std::endl;

    std::cout << "γ2 four-vector: E=" << ev.g2.E
              << ", Px=" << ev.g2.Px
              << ", Py=" << ev.g2.Py
              << ", Pz=" << ev.g2.Pz << std::endl;

    std::cout << "Kinematic fit results:" << std::endl;
    std::cout << "  - imass_kfit:   " << ev.imass_kfit << " GeV" << std::endl;
    std::cout << "  - imassGG_kfit: " << ev.imassGG_kfit << " GeV" << std::endl;
    std::cout << "  - kfit_prob:    " << ev.kfit_prob << std::endl;
}

void HaidisGluexActor::printEventSummary() const {
    std::cout << "\nHaidisGluexActor Statistics:" << std::endl;
    std::cout << "  Events processed: " << eventCount_ << std::endl;
    std::cout << "  Events passed selection: " << passedEventCount_ << std::endl;
    std::cout << "  Errors encountered: " << errorCount_ << std::endl;
    std::cout << std::endl;
}

void HaidisGluexActor::printSeparator(const std::string& title) const {
    const std::string separator(60, '=');
    std::cout << separator << std::endl;
    if (!title.empty()) {
        std::cout << " " << title << std::endl;
        std::cout << separator << std::endl;
    }
}

} // namespace coda
} // namespace ersap
