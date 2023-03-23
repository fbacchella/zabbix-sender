package io.github.hengyunabc.zabbix.sender;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ZabbixDialog implements Closeable {
    public static final String ZABBIX_MAGIC = "ZBXD";
    private static final ByteOrder NETWORK_ORDER = ByteOrder.LITTLE_ENDIAN;
    private static final int DEFAULT_BUFFER_SIZE = 512;

    private final SocketChannel connection;

    public ZabbixDialog(SocketChannel connection) {
        this.connection = connection;
    }

    public int send(ByteBuffer request) throws IOException {
        byte[] payload = new byte[ZABBIX_MAGIC.length() + 1 + 4 + 4 + request.remaining()];
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        payloadBuffer.order(NETWORK_ORDER);
        payloadBuffer.put(ZABBIX_MAGIC.getBytes(StandardCharsets.US_ASCII));
        payloadBuffer.put((byte)1);

        payloadBuffer.putInt(request.remaining());
        payloadBuffer.putInt(0);
        payloadBuffer.put(request);
        payloadBuffer.flip();
        return connection.write(payloadBuffer);
    }

    public ByteBuffer read() throws IOException {
        // normal responseData.length < 100
        byte[] responseData = new byte[DEFAULT_BUFFER_SIZE];
        ByteBuffer bbuffer = ByteBuffer.wrap(responseData);
        bbuffer.order(ByteOrder.LITTLE_ENDIAN);

        InputStream inputStream = connection.socket().getInputStream();
        int readCount = 0;
        // Read the header
        readCount += inputStream.read(responseData);
        String header = readString(bbuffer, 4, StandardCharsets.US_ASCII);
        if (! ZABBIX_MAGIC.equals(header)) {
            throw new IOException("Not a Zabbix connection");
        }
        if (bbuffer.get() != 1) {
            throw new IOException("Not supported Zabbix exchange");
        }
        int size = bbuffer.getInt();
        // Reserved
        bbuffer.getInt();
        int read;
        while (readCount < (size + 13) && readCount < DEFAULT_BUFFER_SIZE && (read = inputStream.read(responseData, readCount, size)) > 0) {
            readCount += read;
        }
        if (readCount != (size + 13)) {
            throw new IOException("Invalid Zabbix exchange, not enough data");
        }
        return bbuffer.slice().limit(size).asReadOnlyBuffer().order(NETWORK_ORDER);
    }

    public String readString(ByteBuffer bbuffer, int size, Charset charset) {
        String content = charset.decode(bbuffer.slice().limit(size)).toString();
        bbuffer.position(bbuffer.position() + size);
        return content;
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
