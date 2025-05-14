package org.jlab.ersap.actor.coda.source.socket;

import com.lmax.disruptor.dsl.Disruptor;
import org.jlab.ersap.actor.util.IASource;

import java.net.Socket;
import java.nio.ByteOrder;
import java.util.concurrent.Callable;

public class SingleSocketStreamReceiver implements IASource, Callable<String> {

    private SocketConnectionHandler handler;
    private Socket connection;

    public SingleSocketStreamReceiver (StreamParameters p) {
        Disruptor<Event> disruptor = new Disruptor<>(Event::new, p.getRingBufferSize(), Runnable::run);
        disruptor.start();

        handler = new SocketConnectionHandler(
                disruptor.getRingBuffer(),
                p.getHost(), p.getPort(),
                ByteOrder.BIG_ENDIAN,
                p.getConnectionTimeout(),
                p.getReadTimeout()
        );

        try {
            connection = handler.establishConnection();
            handler.listenAndPublish(connection);
        } catch (IllegalStateException e) {
            System.err.println("Failed to establish connection: " + e.getMessage());
        }
    }


    @Override
    public Object nextEvent() {
        // This section is designated for the implementation of
        // a potential supplementary singles-finding algorithm.
        return  handler.getNextEvent();
    }

    @Override
    public int getEventCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ByteOrder getByteOrder() {
        return handler.getByteOrder();
    }

    @Override
    public void close() {
        handler.closeConnection(connection);
    }

    @Override
    public String call() throws Exception {
        return null;
    }
}