# How-To Guide: Starting the CODA Data Processing Pipeline with ERSAP from Scratch

This document provides technical instructions for initializing and managing a real-time CODA data processing pipeline using the ERSAP framework. The pipeline includes integration with the Event Transfer (ET) system, CODA DAQ, and an ERSAP-based stream processing chain.

---

## Launch the ET System Interface for ERSAP

In a new terminal window, start the ET system which acts as a buffer between the DAQ and ERSAP:


    et_start -f /tmp/et_SRO_ERSAP -v -d -n 1000 -s 1000000 -p 23911
Parameters:

-f: Path to the ET file (ET system name)

-n: Number of events in memory

-s: Event size in bytes

-p: TCP port number for the ET server

## Start CODA DAQ System
Use the CODA Run Control GUI or terminal interface to:

Select the **_spPilot_** configuration.

Execute the standard run sequence: **_Configure_** → **_Download_** → **_Prestart_** → **_Go_**.

## Launch the ERSAP Data Processing Pipeline
In a new terminal window:

    cd SRO/ersap
    . set_env.sh                   # Run this once per terminal session
    $ERSAP_HOME/bin/ersap_shell
Once inside the ERSAP shell, start the local processing pipeline:

    ersap> run_local

## Stopping the ERSAP Pipeline
Graceful Exit: Press **_CTRL+C_** and wait for a clean shutdown. ERSAP ensures all threads terminate properly.

Forced Termination: Use the following command to forcefully stop the pipeline:

    /home/hatdaq/SRO/ersap/kill_ersap

## Restarting the Pipeline Without Restarting CODA DAQ
You may stop and restart ERSAP independently from CODA:

If you performed a hard kill, reinitialize using:

    ersap_shell
    run_local

If you exited gracefully, start a new pipeline session by repeating only:

    run_local

## Configuring the ERSAP Processing Pipeline
To edit the processing pipeline configuration, launch the ERSAP shell:

    ersap> edit services
This opens a YAML file describing the service composition:

    ---
    io-services:
      reader:
        class: org.jlab.ersap.actor.coda.engine.CodaEtSourceEngine
        name: Source
      writer:
        class: org.jlab.ersap.actor.coda.engine.CodaHistogramSinkEngine
        name: Sink
    
    services:
      - class: org.jlab.ersap.actor.coda.engine.EventIdentificationEngine
        name: EventId
    
    configuration:
      io-services:
        reader:
          et_name: "/tmp/et_SRO_ERSAP"
          et_port: 23911
          et_station: "ersap"
          fifo_capacity: 128
        writer:
          hist_bins: 100
          hist_min: 100
          hist_max: 8000
          hist_titles: "1-15-0,1-15-1,...,1-15-15"
          coincidence: "1-15-0,1-15-1"
          grid_size: 4
    
      services:
        EventId:
          sliding_window: 40
          multiplicity: 2
    
    mime-types:
      - binary/data-evio
      - binary/data-jobj

## Output Histogram Configuration
To control histogram rendering in the accumulation mode, adjust parameters under the 
    
    writer: 
        hist_bins: 100
        hist_min: 100
        hist_max: 8000
        hist_titles: "1-15-0,1-15-1,...,1-15-15"
        coincidence: "1-15-0,1-15-1"
        grid_size: 4

hist_bins: Number of bins

hist_min, hist_max: Range for binning

hist_titles: Histogram channel identifiers

grid_size: Layout matrix (e.g., 4 for 4x4 visualization)

## Notes
The CODA DAQ system can be restarted independently without affecting the ERSAP pipeline.

It is recommended to monitor pipeline behavior during long-running sessions to ensure data integrity and thread consistency.