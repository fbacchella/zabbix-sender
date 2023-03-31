package fr.loghub.zabbix.sender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.loghub.zabbix.ZabbixProtocol;
import lombok.Data;
import lombok.Getter;

/**
 *
 * @author hengyunabc
 *
 */
@Data
public class ZabbixSender {

    private static final Pattern PATTERN = Pattern.compile("([a-z ]+): ([0-9.]+)(; )?");

    @Getter
    private final String host;
    @Getter
    private final int port;
    @Getter
    private final int connectTimeout;
    @Getter
    private final int socketTimeout;
    @Getter
    private final JsonHandler jhandler;

    /**
     * A ZabbixClient with a default timeout of 3 seconds
     * @param host
     * @param port
     * @param jhandler
     */
    public ZabbixSender(String host, int port, JsonHandler jhandler) {
        this(host, port, 3 * 1000, 3 * 1000, jhandler);
    }

    /**
     * A ZabbixClient with the same timeout for connect and socket communications
     * @param host
     * @param port
     * @param timeout
     * @param jhandler
     */
    public ZabbixSender(String host, int port, int timeout, JsonHandler jhandler) {
        this(host, port, timeout, timeout, jhandler);
    }

    /**
     * A ZabbixClient with all explicit settings
     * @param host
     * @param port
     * @param connectTimeout
     * @param socketTimeout
     * @param jhandler
     */
    public ZabbixSender(String host, int port, int connectTimeout, int socketTimeout, JsonHandler jhandler) {
        this.host = host;
        this.port = port;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        this.jhandler = jhandler;
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
        try (SocketChannel socket = SocketChannel.open();
             ZabbixProtocol dialog = new ZabbixProtocol(socket)) {
            socket.socket().setSoTimeout(socketTimeout);
            socket.socket().connect(new InetSocketAddress(host, port), connectTimeout);

            SenderRequest.SenderRequestBuilder builder = SenderRequest.builder();
            Arrays.stream(dataObjectList).forEach(builder::data);
            SenderRequest senderRequest = builder.clock(clock).build();

            ByteBuffer outJsonString = ByteBuffer.wrap(jhandler.serialize(senderRequest.getJsonObject()).getBytes(StandardCharsets.UTF_8));
            dialog.send(outJsonString);
            ByteBuffer responseBuffer = dialog.read();
            String inJsonString = dialog.readString(responseBuffer, responseBuffer.remaining(), StandardCharsets.UTF_8);
            Map<String, Object> responseObject = jhandler.deserialize(inJsonString, Map.class);

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
