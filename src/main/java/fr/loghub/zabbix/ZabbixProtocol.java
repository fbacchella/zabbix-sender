package fr.loghub.zabbix;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * An implementation of a Zabbix exchange using the standard Zabbix header.
 * It does not handle compression nor large packet.
 */
public class ZabbixProtocol implements Closeable {
    private static final byte[] ZABBIX_MAGIC;
    static {
        ZABBIX_MAGIC = "ZBXD".getBytes(StandardCharsets.US_ASCII);
    }
    private static final int HEADER_SIZE = ZABBIX_MAGIC.length + 1 + 4 + 4;

    private final Socket connection;

    public ZabbixProtocol(Socket connection) {
        this.connection = connection;
    }

    /**
     *
     * @param request the request body to send
     * @throws IOException if communication failed
     * @throws IllegalArgumentException if the packet size is too big.
     */
    public void send(byte[] request) throws IOException {
        if (request.length > 1073741824) {
            throw new IllegalArgumentException("Oversize request");
        }
        byte[] payload = Arrays.copyOf(ZABBIX_MAGIC, HEADER_SIZE + request.length);
        payload[4] = 1;
        writeInt(request.length, payload, 5);
        System.arraycopy(request, 0, payload, 13, request.length);
        connection.getOutputStream().write(payload);
        connection.getOutputStream().flush();
    }

    public byte[] read() throws IOException {
        InputStream inputStream = connection.getInputStream();

        byte[] headerBuffer = new byte[HEADER_SIZE];

        int headerReadCount = 0;
        int headerRead = 0;
        while (headerReadCount < HEADER_SIZE && (headerRead = inputStream.read(headerBuffer, headerReadCount, HEADER_SIZE - headerReadCount)) > 0) {
            headerReadCount += headerRead;
        }
        if (headerRead < 0) {
            throw new IOException("Connection closed");
        }

        boolean magicStatus = Arrays.equals(headerBuffer, 0, ZABBIX_MAGIC.length, ZABBIX_MAGIC, 0, ZABBIX_MAGIC.length);
        if (! magicStatus) {
            throw new IOException("Not a Zabbix connection");
        }
        if (headerBuffer[4] != 1) {
            throw new IOException("Not supported Zabbix exchange");
        }
        int size = readInt(headerBuffer, 5);
        if (size > 1073741824) {
            throw new IOException("Oversize response");
        }
        int reserved = readInt(headerBuffer, 9);
        if (reserved != 0) {
            throw new IOException("Not supported Zabbix exchange");
        }
        byte[] payloadBuffer = new byte[size];
        int readCount = 0;
        int read = 0;
        while (readCount < size && (read = inputStream.read(payloadBuffer, readCount, size - readCount)) > 0) {
            readCount += read;
        }
        if (read < 0) {
            throw new IOException("Connection closed");
        }

        return payloadBuffer;
   }

    static int readInt(byte[] buffer, int offset) {
        return (buffer[offset + 3] & 0xFF) << 24
             | (buffer[offset + 2] & 0xFF) << 16
             | (buffer[offset + 1] & 0xFF) << 8
             | buffer[offset] & 0xFF;
    }

    static void writeInt(int value, byte[] buffer, int offset) {
        buffer[offset + 3] = (byte) ((value >> 24) & 0xFF);
        buffer[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buffer[offset] = (byte) (value & 0xFF);
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        connection.close();
    }

}
