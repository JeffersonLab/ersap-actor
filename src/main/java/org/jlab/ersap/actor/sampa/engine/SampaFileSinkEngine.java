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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private String f_name;
    private static String FILE_OUTPUT = "file_output";
    private boolean file_output = false;

    private static String FEC_COUNT = "fec_count";

    private Map<Integer, double[]> frame = new HashMap<>();
    private int chNum = 80;

    private Gson gson = new Gson();


    @Override
    protected FileOutputStream createWriter(Path file, JSONObject opts)
            throws EventWriterException {

        if (opts.has(FILE_OUTPUT)) {
            if (opts.getString(FILE_OUTPUT).equalsIgnoreCase("true")) {
                file_output = true;
            } else {
                file_output = false;
            }
        }

        try {
            f_name = file.toString();
            return new FileOutputStream(f_name);
        } catch (IOException e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected void closeWriter() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        if (file_output) {
            try {
                ByteBuffer b = (ByteBuffer) event;
                ByteBuffer[] data = null;

                try {
                    data = DasDataType.deserialize(b);
                } catch (ErsapException e) {
                    e.printStackTrace();
                }
                // How much data do we have?
                assert data != null;
                int sampleLimit = data[0].limit() / 2;

//                double[] dataPts = new double[sampleLimit];
                List<Double> dataPts = new ArrayList<>();

                for (int channel = 0; channel < chNum; channel++) {
                    for (int sample = 0; sample < sampleLimit; sample++) {
                        try {
                            if(data[channel].getShort(2 * sample) > 0) {
                                dataPts.add((double) data[channel].getShort(2 * sample));
//                                dataPts[sample] = data[channel].getShort(2 * sample); // ADC sample
                            }
                        } catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                    }
                    double[] arr = dataPts.stream().mapToDouble(Double::doubleValue).toArray();
                    dataPts.clear();
                    frame.put(channel, arr);
                }
                writer.write(gson.toJson(frame).getBytes());
                writer.write("\n".getBytes());
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
