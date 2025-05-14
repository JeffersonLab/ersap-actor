package org.jlab.ersap.actor.coda.source.socket;

import com.lmax.disruptor.RingBuffer;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A specific implementation of AbstractConnectionHandler for managing
 * socket connections and publishing data to a Disruptor RingBuffer.
 */
public class SocketConnectionHandler extends AbstractConnectionHandler {

    private static final Logger LOGGER = Logger.getLogger(SocketConnectionHandler.class.getName());

    private final String host;
    private final int port;
    private final ByteOrder byteOrder;
    private final int connectionTimeout; // Connection timeout in milliseconds.
    private final int readTimeout;      // Read timeout in milliseconds.

    /**
     * Constructor for SocketConnectionHandler.
     *
     * @param disruptorRingBuffer the RingBuffer instance for managing events.
     * @param host                the hostname or IP address of the socket server.
     * @param port                the port number of the socket server.
     * @param byteOrder           the ByteOrder used for reading data.
     * @param connectionTimeout   the timeout for establishing the connection in milliseconds.
     * @param readTimeout         the timeout for reading data in milliseconds.
     */
    public SocketConnectionHandler(
            RingBuffer<Event> disruptorRingBuffer,
            String host,
            int port,
            ByteOrder byteOrder,
            int connectionTimeout,
            int readTimeout) {
        super(disruptorRingBuffer); // Initialize the superclass
        this.host = host;
        this.port = port;
        this.byteOrder = byteOrder;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public Socket establishConnection() {
        try {
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), connectionTimeout);
            socket.setSoTimeout(readTimeout); // Set timeout for reading data.
            LOGGER.info("Socket connection established.");
            return socket;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to establish connection: {0}", e.getMessage());
            throw new IllegalStateException("Unable to establish connection to " + host + ":" + port, e);
        }
    }

    @Override
    public void listenAndPublish(Object connection) {
        if (!(connection instanceof Socket)) {
            throw new IllegalArgumentException("Connection must be a Socket instance.");
        }

        Socket socket = (Socket) connection;

        new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                    byte[] data = receiveData(socket);
                    if (data != null) {
                        publishEvent(data);
                    }
                }
            } finally {
                closeConnection(socket);
            }
        }).start();
    }

    @Override
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public void closeConnection(Object connection) {
        if (connection instanceof Socket) {
            try {
                ((Socket) connection).close();
                LOGGER.info("Socket connection closed.");
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to close socket: {0}", e.getMessage());
            }
        } else {
            throw new IllegalArgumentException("Invalid connection object. Expected a Socket.");
        }
    }

    @Override
    protected byte[] receiveData(Object connection) {
        if (!(connection instanceof Socket)) {
            throw new IllegalArgumentException("Connection must be a Socket instance.");
        }

        Socket socket = (Socket) connection;

        try (DataInputStream inputStream = new DataInputStream(socket.getInputStream())) {
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead > 0) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                return data;
            } else if (bytesRead == -1) {
                LOGGER.warning("End of stream reached. Closing connection.");
                return null;
            }
        } catch (SocketTimeoutException e) {
            LOGGER.warning("Read operation timed out.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error while reading data from socket: {0}", e.getMessage());
        }

        return null;
    }

    /**
     * Publishes the received data to the RingBuffer.
     *
     * @param data the received data to be published.
     */
    private void publishEvent(byte[] data) {
        long sequence = super.disruptorRingBuffer.next(); // Reserve next slot
        try {
            Event event = super.disruptorRingBuffer.get(sequence); // Get entry for sequence
            event.setData(data); // Set data in event
        } finally {
            super.disruptorRingBuffer.publish(sequence); // Publish event
        }
    }
}