package org.jlab.ersap.actor.coda.oldvtp;

import j4np.hipo5.data.Bank;
import j4np.hipo5.data.Event;
import j4np.hipo5.data.Schema;
import j4np.hipo5.io.HipoWriter;

import java.util.List;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 2/23/23
 * @project ersap-coda
 */
public class Fadc2Hipo {
    private final Schema rawSchema;
    private final HipoWriter w;

    public Fadc2Hipo(String fileName) {
        Schema.SchemaBuilder builder = new Schema.SchemaBuilder("raw::data", 1200, 1);
        builder.addEntry("crate", "I", "crate number");
        builder.addEntry("slot", "I", "slot number");
        builder.addEntry("channel", "I", "channel number");
        builder.addEntry("charge", "I", "accumulated charge");
        builder.addEntry("time", "L", "time of the hit");
        rawSchema = builder.build();
//        rawSchema.show();

        w = new HipoWriter();
        w.getSchemaFactory().addSchema(rawSchema);
//        w.getSchemaFactory().show();

        w.open(fileName);
    }

    public synchronized void evtWrite(List<VAdcHit> hits) {
        Event event = new Event();
        Bank rBank = new Bank(rawSchema, hits.size());
        int row = 0;
        for (VAdcHit hit : hits) {
            rBank.putInt(0, row, hit.getCrate());
            rBank.putInt(1, row, hit.getSlot());
            rBank.putInt(2, row, hit.getChannel());
            rBank.putInt(3, row, hit.getCharge());
            rBank.putLong(4, row, hit.getTime());
            row++;
        }
//        rBank.show();
        event.reset();
        event.write(rBank);
//        event.scanShow();
        w.addEvent(event);
    }

    public void close() {
        w.close();
    }
}

