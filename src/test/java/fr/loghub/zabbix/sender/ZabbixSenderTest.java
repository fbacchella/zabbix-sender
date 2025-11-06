package fr.loghub.zabbix.sender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.alibaba.fastjson.JSON;

public class ZabbixSenderTest {

    private static final JsonHandler jhandler = new JsonHandler() {

        @Override
        public String serialize(Object data) {
            return JSON.toJSONString(data);
        }

        @Override
        public <T> T deserialize(String content, Class<T> clazz) {
            return JSON.parseObject(content, clazz);
        }
    };

    String host = "127.0.0.1";
    int port = 49156;
    private final CompletableFuture<byte[]> queryProcessor = new CompletableFuture<>();
    private final ZabbixServer server = new ZabbixServer("response.blob", queryProcessor::complete);

    @Before
    public void startServer() throws InterruptedException {
        server.start();
        Assert.assertTrue(server.waitStarted(1, TimeUnit.SECONDS));
        Thread.sleep(100);
    }

    @After
    public void stopServer() throws ExecutionException, InterruptedException, TimeoutException {
        if (server.serverAlive()) {
            server.interrupt();
            Assert.assertTrue(server.waitStopped(1, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testSuccess() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        ZabbixSender zabbixClient = ZabbixSender.builder().host(host).port(port).jhandler(jhandler).build();
        DataObject dataObject = DataObject.builder()
                                          .host("172.17.42.1")
                                          .key("healthcheck", "dw", "notificationserver")
                                          .value(List.of(1, 2))
                                          .clock(Instant.now())
                                          .build();
        SenderResult result = zabbixClient.send(dataObject);
        Assert.assertTrue(result.success());
        byte[] query = queryProcessor.get();
        Map<?, ?> content = jhandler.deserialize(new String(query, StandardCharsets.UTF_8), Map.class);
        Assert.assertEquals("sender data", content.get("request"));
        @SuppressWarnings("unchecked")
        Map<?, ?> objectMap = ((List<Map<?, ?>>)content.get("data")).get(0);
        Assert.assertEquals(dataObject.getClock().getNano(), objectMap.get("ns"));
        Assert.assertEquals(dataObject.getClock().getEpochSecond(), ((Number)objectMap.get("clock")).longValue());
        Assert.assertEquals("healthcheck[dw,notificationserver]", objectMap.get("key"));
        Assert.assertEquals(dataObject.getValue(), objectMap.get("value"));
        Assert.assertEquals(dataObject.getHost(), objectMap.get("host"));
        server.interrupt();
        Assert.assertTrue(server.waitStopped(1, TimeUnit.SECONDS));
    }

    @Test
    public void testFailure1() throws IOException {
        testFailure(bb -> bb.put((byte) 0), "Connection closed");
    }

    @Test
    public void testFailure2() throws IOException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 2);
            bb.putInt(Integer.MAX_VALUE);
            bb.putInt(0);
        }, "Not supported Zabbix exchange");
    }

    @Test
    public void testFailure3() throws IOException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 1);
            bb.putInt(Integer.MAX_VALUE);
            bb.putInt(0);
        }, "Oversize response");
    }

    @Test
    public void testFailure4() throws IOException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 1);
            bb.putInt( 1);
            bb.putInt(1);
        }, "Not supported Zabbix exchange");
    }

    @Test
    public void testFailure5() throws IOException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 1);
            bb.putInt( 1);
            bb.putInt(0);
        }, "Connection closed");
    }

    private void testFailure(Consumer<ByteBuffer> filler, String message) throws IOException {
        try (SocketChannel client = SocketChannel.open(new InetSocketAddress(host, port))) {
            ByteBuffer bad = ByteBuffer.allocate(13);
            bad.order(ByteOrder.LITTLE_ENDIAN);
            filler.accept(bad);
            bad.flip();
            client.write(bad);
        }
        ExecutionException failure = Assert.assertThrows(ExecutionException.class, () -> server.waitStopped(1, TimeUnit.SECONDS));
        Assert.assertEquals(IOException.class, failure.getCause().getClass());
        Assert.assertEquals(message, failure.getCause().getMessage());
    }

}
