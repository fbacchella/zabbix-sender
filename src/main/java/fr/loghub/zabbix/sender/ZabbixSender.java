package fr.loghub.zabbix.sender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;

import fr.loghub.zabbix.ZabbixProtocol;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 *
 * @author hengyunabc
 *
 */
@Data
public class ZabbixSender {

    private static final Pattern PATTERN = Pattern.compile("([a-z][a-z ]*): (\\d[\\d.]*)(?:; |$)");

    @Accessors(fluent = true)
    public static class Builder {
        @Setter
        private String host;
        @Setter
        private int port;
        private long connectTimeout = 3000;
        private long socketTimeout = 3000;
        @Setter
        private JsonHandler jhandler;
        @Setter
        private SocketFactory factory = SocketFactory.getDefault();

        public Builder connectTimeout(long value, TimeUnit unit) {
            connectTimeout = TimeUnit.MILLISECONDS.convert(value, unit);
            if (connectTimeout < 0 || connectTimeout > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Out of range timeout: " + connectTimeout + "ms");
            }
            return this;
        }
        public Builder socketTimeout(long value, TimeUnit unit) {
            socketTimeout = TimeUnit.MILLISECONDS.convert(value, unit);
            if (connectTimeout < 0 || connectTimeout > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Out of range timeout: " + connectTimeout + "ms");
            }
            return this;
        }
        public Builder sslContext(SSLContext sslContext) {
            factory = sslContext.getSocketFactory();
            return this;
        }
        public Builder socketFactory(SocketFactory factory) {
            this.factory = factory;
            return this;
        }
        public ZabbixSender build() {
            return new ZabbixSender(this);
        }
    }
    public static ZabbixSender.Builder builder() {
        return new ZabbixSender.Builder();
    }
    @Getter
    private final String host;
    @Getter
    private final int port;
    @Getter
    private final long connectTimeout;
    @Getter
    private final long socketTimeout;
    @Getter
    private final JsonHandler jhandler;
    @Getter
    private final SocketFactory factory;

    private ZabbixSender(Builder builder) {
        host = builder.host;
        port = builder.port;
        connectTimeout = builder.connectTimeout;
        socketTimeout = builder.socketTimeout;
        jhandler = builder.jhandler;
        factory = builder.factory;
    }

    public SenderResult send(DataObject... dataObjectList) throws IOException {
        return send(Instant.now(), dataObjectList);
    }

    /**
     *
     * @param clock
     * @param dataObjectList
     * @return
     * @throws IOException
     */
    public SenderResult send(Instant clock, DataObject... dataObjectList) throws IOException {
        try (Socket socket = factory.createSocket();
             ZabbixProtocol dialog = new ZabbixProtocol(socket)) {
            socket.setSoTimeout((int)socketTimeout);
            socket.connect(new InetSocketAddress(host, port), (int)connectTimeout);

            SenderRequest.SenderRequestBuilder builder = SenderRequest.builder();
            Arrays.stream(dataObjectList).forEach(builder::data);
            SenderRequest senderRequest = builder.clock(clock).build();

            dialog.send(jhandler.serialize(senderRequest.getJsonObject()).getBytes(StandardCharsets.UTF_8));
            byte[] responseBuffer = dialog.read();
            @SuppressWarnings("unchecked")
            Map<String, Object> responseObject = jhandler.deserialize(new String(responseBuffer, StandardCharsets.UTF_8), Map.class);

            String response = (String) responseObject.get("response");
            if (!"success".equals(response)) {
                throw new IOException("Zabbix failure: " + responseObject);
            }
            return parseResultsString(responseObject.get("info").toString());
        }
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
        Optional.ofNullable(infoValues.get("processed")).map(Number::intValue).ifPresent(resultBuilder::processed);
        Optional.ofNullable(infoValues.get("failed")).map(Number::intValue).ifPresent(resultBuilder::failed);
        Optional.ofNullable(infoValues.get("total")).map(Number::intValue).ifPresent(resultBuilder::total);
        Optional.ofNullable(infoValues.get("seconds spent")).map(Number::floatValue).ifPresent(resultBuilder::spentSeconds);
        return resultBuilder.build();
    }

}
