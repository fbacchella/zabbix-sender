package io.github.hengyunabc.zabbix.sender;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author hengyunabc
 *
 */
public class ZabbixSender {
    private static final Pattern PATTERN = Pattern.compile("[^0-9\\.]+");
    private final static Charset UTF8 = Charset.forName("UTF-8");

    @Setter @Getter
    String host;
    @Setter @Getter
    int port;
    @Setter @Getter
    int connectTimeout = 3 * 1000;
    @Setter @Getter
    int socketTimeout = 3 * 1000;

    public ZabbixSender(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public ZabbixSender(String host, int port, int connectTimeout, int socketTimeout) {
        this(host, port);
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
    }

    public SenderResult send(DataObject dataObject) throws IOException {
        return send(dataObject, System.currentTimeMillis() / 1000);
    }

    /**
     *
     * @param dataObject
     * @param clock
     *            TimeUnit is SECONDS.
     * @return
     * @throws IOException
     */
    public SenderResult send(DataObject dataObject, long clock) throws IOException {
        return send(Collections.singletonList(dataObject), clock);
    }

    public SenderResult send(List<DataObject> dataObjectList) throws IOException {
        return send(dataObjectList, System.currentTimeMillis() / 1000);
    }

    /**
     *
     * @param dataObjectList
     * @param clock
     *            TimeUnit is SECONDS.
     * @return
     * @throws IOException
     */
    public SenderResult send(List<DataObject> dataObjectList, long clock) throws IOException {
        try (Socket socket = new Socket()){
            socket.setSoTimeout(socketTimeout);
            socket.connect(new InetSocketAddress(host, port), connectTimeout);

            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            SenderRequest senderRequest = SenderRequest.builder()
                                                       .data(dataObjectList)
                                                       .clock(clock)
                                                       .build();
            outputStream.write(senderRequest.toBytes());

            outputStream.flush();

            // normal responseData.length < 100
            byte[] responseData = new byte[512];

            int readCount = 0;

            while (true) {
                int read = inputStream.read(responseData, readCount, responseData.length - readCount);
                if (read <= 0) {
                    break;
                }
                readCount += read;
            }

            SenderResult.SenderResultBuilder resultBuilder = SenderResult.builder();
            resultBuilder.returnEmptyArray(readCount < 13);

            // header('ZBXD\1') + len + 0
            // 5 + 4 + 4
            String jsonString = new String(responseData, 13, readCount - 13, UTF8);
            JSONObject json = JSON.parseObject(jsonString);
            String info = json.getString("info");
            // example info: processed: 1; failed: 0; total: 1; seconds spent:
            // 0.000053
            // after split: [, 1, 0, 1, 0.000053]
            String[] split = PATTERN.split(info);

            resultBuilder.processed(Integer.parseInt(split[1]));
            resultBuilder.failed(Integer.parseInt(split[2]));
            resultBuilder.total(Integer.parseInt(split[3]));
            resultBuilder.spentSeconds(Float.parseFloat(split[4]));
            return resultBuilder.build();
        }
    }
}
