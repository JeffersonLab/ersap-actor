package org.jlab.ersap.actor.datatypes;

/**
 * Copyright (c) 2021, Jefferson Science Associates, all rights reserved.
 * See LICENSE.txt file.
 * Thomas Jefferson National Accelerator Facility
 * Experimental Physics Software and Computing Infrastructure Group
 * 12000, Jefferson Ave, Newport News, VA 23606
 * Phone : (757)-269-7100
 *
 * @author gurjyan on 8/31/22
 * @project ersap-sampa
 */
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.ErsapSerializer;
import org.jlab.ersap.actor.util.AUtil;

import java.io.IOException;
import java.nio.ByteBuffer;


public class JavaObjectType {
    private JavaObjectType() { }

    private static class JavaSerializer implements ErsapSerializer {

        @Override
        public ByteBuffer write(Object data) throws ErsapException {
            ByteBuffer bb = null;
            try {
                bb = ByteBuffer.wrap(AUtil.toByteArray(data));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bb;
        }

        @Override
        public Object read(ByteBuffer buffer) throws ErsapException {
            Object b = null;
            try {
                b = AUtil.toObject(buffer.array());
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            return b;
        }
    }

    public static final EngineDataType JOBJ =
            new EngineDataType("binary/data-jobj", EngineDataType.BYTES.serializer());
}