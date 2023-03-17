SAMPA SRO

Follow the link for more description of the ERSAP based GEM detector SRO.
https://wiki.jlab.org/epsciwiki/index.php/SAMPA_SRO#How_to_write_your_ERSAP_processor_engine.3F

Raw data JSON format: 
{"channel_number", [ dataPoint1, dataPoint2, ...]}

The channel number begins with 0, representing the 
initial channel of the FEC configuration. 
For example, based on the following YAML representation of ERSAP, 
the 0 channel corresponds to FEC1: (counting from 0).    

    configuration:
    global:
    fec_count: 3
    io-services:
    reader:
    port: 6000
    fec: "1,2,3"
    writer:
    file_output: "true"
    fec_count: 3

Please note that each FEC offers 160 channels. Hence, channel=160 will 
be the first channel of FEC2 and channel=320 will be the first channel of FEC3.
Importantly, YAML must have consistent fec count settings, and the FEC 
assignment must match the SAMPA DAQ command line (e.g., fec: "1,2,3" must 
match treadout —mask 0xE). Notice that treadout mask specifies the active FEC 
position in a 5-bit word (we have a total of 5 FEC cards): 
FEC0 is bit 0 and FEC4 is bit 5.