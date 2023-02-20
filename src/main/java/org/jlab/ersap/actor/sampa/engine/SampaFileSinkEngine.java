package org.jlab.ersap.actor.sampa.engine;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventWriterService;
import org.jlab.epsci.ersap.std.services.EventWriterException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

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
                writer.write(b.array());
                System.out.println("DDD "+ evt_count);
                if (evt_count >= 1000) {
                    writer.flush();
                    writer.close();
                    f_count++;
                    writer = new FileOutputStream(f_name + "_" + f_count);
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
