package org.jlab.ersap.actor.sampa.engine;

import com.google.gson.Gson;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventWriterService;
import org.jlab.epsci.ersap.std.services.EventWriterException;
import org.jlab.ersap.actor.datatypes.DasDataType;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 9/26/22
 * @project ersap-sampa
 */
public class SampaFileSinkEngine extends AbstractEventWriterService<FileOutputStream> {
    private int evt_count;
    private int f_count;
    private String f_name;
    private static String FILE_OUTPUT = "file_output";
    private boolean file_output = false;

    private static String FEC_COUNT = "fec_count";

    private Map<Integer, double[]> frame = new HashMap<>();
    private int chNum;

    private Gson gson = new Gson();


    @Override
    protected FileOutputStream createWriter(Path file, JSONObject opts)
            throws EventWriterException {

        if (opts.has(FILE_OUTPUT)) {
            if(opts.getString(FILE_OUTPUT).equalsIgnoreCase("true")) {
                file_output = true;
            } else {
                file_output = false;
            }
        }
        if (opts.has(FEC_COUNT)) {
            // Each FEC has 2 GBT stream, each having 80 channel data
            chNum = 80 * opts.getInt(FEC_COUNT) * 2;
        }

        try {
            evt_count = 0;
            f_name = file.toString();
            return new FileOutputStream(f_name+"_"+f_count);
        } catch (IOException e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected void closeWriter() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        if(file_output) {
            try {
                evt_count++;
                ByteBuffer b = (ByteBuffer) event;
                ByteBuffer[] data = null;

                try {
                    data = DasDataType.deserialize(b);
                } catch (ErsapException e) {
                    e.printStackTrace();
                }
                // How much data do we have?
                assert data != null;
                int sampleLimit = data[0].limit()/2;

                double [] dataPts = new double[sampleLimit];

                for (int channel = 0; channel < chNum; channel++) {
                    for (int sample = 0; sample < sampleLimit; sample++) {
                        try {
                            dataPts[sample] = data[channel].getShort(2 * sample); // ADC sample
                        } catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                    }
                    frame.put(channel,dataPts);
                }
                writer.write(gson.toJson(frame).getBytes());
                 if (evt_count >= 1000) {
                    writer.flush();
                    writer.close();
                    f_count++;
                    writer = new FileOutputStream(f_name + "_" + f_count);
                    System.out.println("INFO File = "+ f_name + "_" + f_count);
                    evt_count = 0;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.BYTES;
//        return SampaDasType.SAMPA_DAS;
    }
}
