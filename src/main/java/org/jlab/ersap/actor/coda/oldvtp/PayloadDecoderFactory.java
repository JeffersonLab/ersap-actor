package org.jlab.ersap.actor.coda.oldvtp;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

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
public class PayloadDecoderFactory extends BasePooledObjectFactory<VPayloadDecoder> {

    @Override
    public VPayloadDecoder create() throws Exception {
        return new VPayloadDecoder();
    }

    @Override
    public PooledObject<VPayloadDecoder> wrap(VPayloadDecoder payloadDecoder) {
        return new DefaultPooledObject<VPayloadDecoder>(payloadDecoder);
    }
}
