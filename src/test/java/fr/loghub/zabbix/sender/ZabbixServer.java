package fr.loghub.zabbix.sender;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import fr.loghub.zabbix.ZabbixProtocol;

public class ZabbixServer extends Thread implements Thread.UncaughtExceptionHandler {

    private final String datapath;
    private final CountDownLatch started = new CountDownLatch(0);
    private final Consumer<ByteBuffer> queryProcessor;
    private final CompletableFuture<Boolean> doneProcessing = new CompletableFuture();

    public ZabbixServer(String datapath, Consumer<ByteBuffer> queryProcessor) {
        this.datapath = datapath;
        this.queryProcessor = queryProcessor;
        this.setUncaughtExceptionHandler(this);
    }

    public void run() {
        try (ServerSocketChannel server = ServerSocketChannel.open()){
            server.bind(new InetSocketAddress("127.0.0.1", 49156));
            started.countDown();
            while (true) {
                try (SocketChannel client = server.accept();
                     ZabbixProtocol handler = new ZabbixProtocol(client)
                ) {
                    ByteBuffer queryData = handler.read();
                    queryProcessor.accept(queryData);
                    try (InputStream datastream = getClass().getClassLoader().getResourceAsStream(datapath)) {
                        ByteBuffer content = ByteBuffer.wrap(datastream.readAllBytes());
                        client.write(content);
                    }
                }
            }
        } catch (ClosedByInterruptException e) {
            doneProcessing.complete(true);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    public boolean waitStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return started.await(timeout, unit);
    }

    public boolean waitStopped(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return doneProcessing.get(timeout, unit);
    }
    /**
     * Method invoked when the given thread terminates due to the
     * given uncaught exception.
     * <p>Any exception thrown by this method will be ignored by the
     * Java Virtual Machine.
     *
     * @param t the thread
     * @param e the exception
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        doneProcessing.completeExceptionally(e);
    }

}
