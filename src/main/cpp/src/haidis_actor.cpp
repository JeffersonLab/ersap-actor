/*
 * Copyright (c) 2025, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * Implementation of HAIDIS actor for ET system event processing
 * @author gurjyan
 * @project ersap-actor
 */

#include "HaidisActor.hpp"
#include <ersap/stdlib/json_utils.hpp>
#include <iostream>
#include <iomanip>
#include <sstream>
#include <cstring>

// C interface for engine creation
extern "C"
std::unique_ptr<ersap::Engine> create_engine()
{
    return std::make_unique<ersap::coda::HaidisActor>();
}

namespace ersap {
namespace coda {

HaidisActor::~HaidisActor() {
    cleanupET();
}

ersap::EngineData HaidisActor::configure(ersap::EngineData& input) {
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
                std::cout << "HaidisActor configuration:" << std::endl;
                std::cout << "  - ET filename: " << etFilename_ << std::endl;
                std::cout << "  - ET host: " << etHost_ << std::endl;
                std::cout << "  - ET port: " << etPort_ << std::endl;
                std::cout << "  - Station name: " << stationName_ << std::endl;
                std::cout << "  - Verbose: " << verbose_ << std::endl;
            }

        } catch (const std::exception& e) {
            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Error parsing configuration: " + std::string(e.what()));
            std::cerr << "Error parsing HaidisActor configuration: " << e.what() << std::endl;
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
        std::cout << "HaidisActor configured successfully and connected to ET system" << std::endl;
    }

    return output;
}

ersap::EngineData HaidisActor::execute(ersap::EngineData& input) {
    auto output = ersap::EngineData{};

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

        // Validate payload size - expecting 16 doubles
        if (data_len < EXPECTED_SIZE) {
            std::cerr << "Warning: Received " << data_len
                      << " bytes, expected minimum " << EXPECTED_SIZE << " bytes" << std::endl;

            // Return event to ET system
            status = et_event_put(etSys_, etAtt_, pe);
            if (status != ET_OK) {
                std::cerr << "Error: Failed to return event to ET (status " << status << ")" << std::endl;
            }

            output.set_status(ersap::EngineStatus::ERROR);
            output.set_description("Invalid ET event size: " + std::to_string(data_len) +
                                   " bytes, expected " + std::to_string(EXPECTED_SIZE));
            errorCount_++;
            return output;
        }

        // Interpret data as array of doubles
        double* doubles = static_cast<double*>(data_ptr);

        // Process and print the four-vectors
        if (verbose_) {
            printSeparator("Physics Event Data");
        }
        printFourVectors(doubles);
        if (verbose_) {
            printSeparator();
        }

        // Copy the 16 doubles into a vector for output
        std::vector<double> output_data(doubles, doubles + 16);

        // Return event to ET system
        status = et_event_put(etSys_, etAtt_, pe);
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

        // Set success status and output array of doubles
        output.set_data(ersap::type::ARRAY_DOUBLE, output_data);

    } catch (const std::exception& e) {
        output.set_status(ersap::EngineStatus::ERROR);
        output.set_description("Error processing ET event: " + std::string(e.what()));
        std::cerr << "Error in HaidisActor: " << e.what() << std::endl;
        errorCount_++;
    }

    return output;
}

ersap::EngineData HaidisActor::execute_group(const std::vector<ersap::EngineData>&) {
    // Group processing not implemented for this actor
    return {};
}

std::vector<ersap::EngineDataType> HaidisActor::input_data_types() const {
    return { ersap::type::SINT32, ersap::type::JSON };
}

std::vector<ersap::EngineDataType> HaidisActor::output_data_types() const {
    return { ersap::type::ARRAY_DOUBLE, ersap::type::JSON };
}

std::set<std::string> HaidisActor::states() const {
    return {}; // Stateless engine
}

std::string HaidisActor::name() const {
    return "HaidisActor";
}

std::string HaidisActor::author() const {
    return "Jefferson Lab";
}

std::string HaidisActor::description() const {
    return "ERSAP actor interfacing with ET system to process physics four-vector data. "
           "Receives SINT32 trigger from Java source, reads events from ET, and processes "
           "particle physics data (π+, π-, γ1, γ2 four-vectors).";
}

std::string HaidisActor::version() const {
    return "1.0.0";
}

// Private helper methods

bool HaidisActor::initializeET() {
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
    et_station_config_setrestore(statconfig, ET_STATION_RESTORE_OUT);
    et_station_config_setprescale(statconfig, 1);
    et_station_config_setcue(statconfig, 10);
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

void HaidisActor::cleanupET() {
    if (!etConnected_) {
        return;
    }

    if (verbose_) {
        std::cout << "Cleaning up ET system connection..." << std::endl;
    }

    int status;

    if (etAtt_ != nullptr) {
        status = et_station_detach(etSys_, etAtt_);
        if (status != ET_OK) {
            std::cerr << "Warning: Failed to detach from station (status "
                      << status << ")" << std::endl;
        }
        etAtt_ = nullptr;
    }

    if (etSys_ != nullptr) {
        status = et_close(etSys_);
        if (status != ET_OK) {
            std::cerr << "Warning: Failed to close ET system (status "
                      << status << ")" << std::endl;
        }
        etSys_ = nullptr;
    }

    etStat_ = nullptr;
    etConnected_ = false;

    if (verbose_) {
        std::cout << "ET cleanup complete" << std::endl;
    }
}

void HaidisActor::printFourVectors(const double* data) const {
    std::cout << "π+ four-vector: E=" << data[0]
              << ", Px=" << data[1]
              << ", Py=" << data[2]
              << ", Pz=" << data[3] << std::endl;

    std::cout << "π- four-vector: E=" << data[4]
              << ", Px=" << data[5]
              << ", Py=" << data[6]
              << ", Pz=" << data[7] << std::endl;

    std::cout << "γ1 four-vector: E=" << data[8]
              << ", Px=" << data[9]
              << ", Py=" << data[10]
              << ", Pz=" << data[11] << std::endl;

    std::cout << "γ2 four-vector: E=" << data[12]
              << ", Px=" << data[13]
              << ", Py=" << data[14]
              << ", Pz=" << data[15] << std::endl;
}

void HaidisActor::printEventSummary() const {
    std::cout << "\nHaidisActor Statistics:" << std::endl;
    std::cout << "  Events processed: " << eventCount_ << std::endl;
    std::cout << "  Errors encountered: " << errorCount_ << std::endl;
    std::cout << std::endl;
}

void HaidisActor::printSeparator(const std::string& title) const {
    const std::string separator(60, '=');
    std::cout << separator << std::endl;
    if (!title.empty()) {
        std::cout << " " << title << std::endl;
        std::cout << separator << std::endl;
    }
}

} // namespace coda
} // namespace ersap
