package fr.loghub.zabbix.sender;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Assert;
import org.junit.Test;

import com.alibaba.fastjson.JSON;

import fr.loghub.zabbix.AutoCA;

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

    private final CompletableFuture<byte[]> queryProcessor = new CompletableFuture<>();
    private final CompletableFuture<Socket> socketProcessor = new CompletableFuture<>();

    private ZabbixServer startServer(ZabbixServer server) throws InterruptedException {
        Assert.assertTrue(server.waitStarted(1, TimeUnit.SECONDS));
        return server;
    }

    private void runTest(ZabbixSender zabbixClient) throws IOException, ExecutionException, InterruptedException {
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
    }

    private void complete(Socket s, byte[] data) {
        queryProcessor.complete(data);
        socketProcessor.complete(s);
    }

    @Test(timeout = 5000)
    public void testSuccess() throws IOException, ExecutionException, InterruptedException {
        try (ZabbixServer server = new ZabbixServer("response.blob", this::complete)) {
            Assert.assertTrue(server.waitStarted(1, TimeUnit.SECONDS));
            ZabbixSender zabbixClient = ZabbixSender.builder()
                                                    .address(server.getAddress())
                                                    .jhandler(jhandler)
                                                    .build();
            runTest(zabbixClient);
        }
    }

    // AutoCA is quite slow
    @Test(timeout = 10000)
    public void testWithSSL() throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException,
                                             OperatorCreationException, UnrecoverableKeyException,
                                             KeyManagementException, ExecutionException, InterruptedException {
        KeyStore ks = AutoCA.getKeyStore("cn=localhost", InetAddress.getLoopbackAddress());
        SSLContext ctx = AutoCA.createSSLContext(ks);
        SSLParameters params = ctx.getDefaultSSLParameters();
        params.setProtocols(new String[]{"TLSv1.2"});
        try (ZabbixServer secureserver = startServer(new ZabbixServer("response.blob", this::complete, ctx.getServerSocketFactory()))) {
            ZabbixSender zabbixClient = ZabbixSender.builder()
                                                .address(secureserver.getAddress())
                                                .jhandler(jhandler)
                                                .socketFactory(ctx.getSocketFactory())
                                                .sslParameters(params)
                                                .build();
            runTest(zabbixClient);
            SSLSocket socket = (SSLSocket) socketProcessor.get();
            Assert.assertEquals("TLSv1.2", socket.getSession().getProtocol());
        }
     }

    @Test
    public void testFailure1() throws IOException, InterruptedException {
        testFailure(bb -> bb.put((byte) 0), "Connection closed");
    }

    @Test
    public void testFailure2() throws IOException, InterruptedException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 2);
            bb.putInt(Integer.MAX_VALUE);
            bb.putInt(0);
        }, "Not supported Zabbix exchange");
    }

    @Test
    public void testFailure3() throws IOException, InterruptedException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 1);
            bb.putInt(Integer.MAX_VALUE);
            bb.putInt(0);
        }, "Oversize response");
    }

    @Test
    public void testFailure4() throws IOException, InterruptedException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 1);
            bb.putInt(1);
            bb.putInt(1);
        }, "Not supported Zabbix exchange");
    }

    @Test
    public void testFailure5() throws IOException, InterruptedException {
        testFailure(bb -> {
            bb.put("ZBXD".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 1);
            bb.putInt( 1);
            bb.putInt(0);
        }, "Connection closed");
    }

    @Test
    public void testFailure6() throws IOException, InterruptedException {
        testFailure(bb -> {
            bb.put("XXXX".getBytes(StandardCharsets.US_ASCII));
            bb.put((byte) 1);
            bb.putInt( 1);
            bb.putInt(0);
        }, "Not a Zabbix connection");
    }

    private void testFailure(Consumer<ByteBuffer> filler, String message) throws IOException, InterruptedException {
        ZabbixServer serverkeep;
        try (ZabbixServer server = startServer(new ZabbixServer("response.blob", this::complete));
            SocketChannel client = SocketChannel.open(server.getAddress())) {
            ByteBuffer bad = ByteBuffer.allocate(13);
            bad.order(ByteOrder.LITTLE_ENDIAN);
            filler.accept(bad);
            bad.flip();
            client.write(bad);
            serverkeep = server;
        }
        ExecutionException failure = Assert.assertThrows(ExecutionException.class, () -> serverkeep.waitStopped(1, TimeUnit.SECONDS));
        Assert.assertEquals(IOException.class, failure.getCause().getClass());
        Assert.assertEquals(message, failure.getCause().getMessage());
    }
}
