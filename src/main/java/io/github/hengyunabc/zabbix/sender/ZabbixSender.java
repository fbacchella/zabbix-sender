package io.github.hengyunabc.zabbix.sender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.fastjson.JSON;

import lombok.Getter;

/**
 *
 * @author hengyunabc
 *
 */
public class ZabbixSender {
    private static final Pattern PATTERN = Pattern.compile("([a-z ]+): ([0-9.]+)(; )?");
    public static final String ZABBIX_MAGIC = "ZBXD";

    @Getter
    private final String host;
    @Getter
    private final int port;
    @Getter
    private final int connectTimeout;
    @Getter
    private final int socketTimeout;

    public ZabbixSender(String host, int port, JsonHandler handler) {
        this(host, port, handler, 3 * 1000, 3 * 1000);
    }

    public ZabbixSender(String host, int port, JsonHandler handler, int connectTimeout, int socketTimeout) {
        this.host = host;
        this.port = port;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
    }

    public SenderResult send(DataObject dataObject) throws IOException {
        return send(Instant.now(), dataObject);
    }

    public SenderResult send(DataObject... dataObjectList) throws IOException {
        return send(Instant.now(), dataObjectList);
    }

    /**
     *
     * @param dataObjectList
     * @param clock
     * @return
     * @throws IOException
     */
    public SenderResult send(Instant clock, DataObject... dataObjectList) throws IOException {
        try (Socket socket = new Socket()){
            socket.setSoTimeout(socketTimeout);
            socket.connect(new InetSocketAddress(host, port), connectTimeout);

            SenderRequest.SenderRequestBuilder builder = SenderRequest.builder();
            Arrays.stream(dataObjectList).forEach(builder::data);
            SenderRequest senderRequest = builder.clock(clock).build();

            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(senderRequest.toBytes());
            outputStream.flush();

            // normal responseData.length < 100
            byte[] responseData = new byte[512];
            ByteBuffer bbuffer = ByteBuffer.wrap(responseData);
            bbuffer.order(ByteOrder.LITTLE_ENDIAN);

            InputStream inputStream = socket.getInputStream();
            int readCount = 0;
            // Read the header
            readCount += inputStream.read(responseData, 0, 13);
            String header = readString(bbuffer, 4, StandardCharsets.US_ASCII);
            if (! ZABBIX_MAGIC.equals(header)) {
                throw new IOException("Not a Zabbix connection");
            }
            bbuffer.position(4);
            if (bbuffer.get() != 1) {
                throw new IOException("Not supported Zabbix exchange");
            }
            int size = bbuffer.getInt();
            // Reserved
            bbuffer.getInt();
            int read;
            while (readCount < (size + 13) && (read = inputStream.read(responseData, readCount, size)) > 0) {
                readCount += read;
            }

            SenderResult.SenderResultBuilder resultBuilder = SenderResult.builder();
            resultBuilder.returnEmptyArray(readCount < 13);

            String jsonString = readString(bbuffer, size, StandardCharsets.UTF_8);
            Map<String, Object> responseObject = JSON.parseObject(jsonString);

            String response = (String) responseObject.get("response");
            if (!"success".equals(response)) {
                throw new IOException("Zabbix failure: " + responseObject);
            }
            return parseResultsString(responseObject.get("info").toString());
        }
    }

    private String readString(ByteBuffer bbuffer, int size, Charset charset) {
        String content = charset.decode(bbuffer.slice().limit(size)).toString();
        bbuffer.position(bbuffer.position() + size);
        return content;
    }

    private SenderResult parseResultsString(String results) {
        Matcher m = PATTERN.matcher(results);
        Map<String, Number> infoValues = new HashMap<>();
        while (m.find()) {
            if ("seconds spent".equals(m.group(1))) {
                infoValues.put(m.group(1), Float.parseFloat(m.group(2)));
            } else {
                infoValues.put(m.group(1), Integer.parseInt(m.group(2)));
            }
        }
        SenderResult.SenderResultBuilder resultBuilder = SenderResult.builder();
        resultBuilder.returnEmptyArray(false);
        Optional.ofNullable(infoValues.get("processed")).map(Number::intValue).ifPresent(resultBuilder::processed);
        Optional.ofNullable(infoValues.get("failed")).map(Number::intValue).ifPresent(resultBuilder::failed);
        Optional.ofNullable(infoValues.get("total")).map(Number::intValue).ifPresent(resultBuilder::total);
        Optional.ofNullable(infoValues.get("seconds spent")).map(Number::floatValue).ifPresent(resultBuilder::spentSeconds);
        return resultBuilder.build();
    }

}
