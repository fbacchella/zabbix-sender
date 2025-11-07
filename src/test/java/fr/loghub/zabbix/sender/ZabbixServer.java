package fr.loghub.zabbix.sender;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import javax.net.ServerSocketFactory;

import fr.loghub.zabbix.ZabbixProtocol;

public class ZabbixServer extends Thread implements Thread.UncaughtExceptionHandler, Closeable {
    private final CountDownLatch started = new CountDownLatch(1);
    private final BiConsumer<Socket, byte[]> queryProcessor;
    private final CompletableFuture<Boolean> doneProcessing = new CompletableFuture<>();
    private final ServerSocketFactory socketFactory;
    private final AtomicReference<ServerSocket> socketHolder = new AtomicReference<>();
    private final URL dataurl;

    public ZabbixServer(String datapath, BiConsumer<Socket, byte[]> queryProcessor) {
        this(datapath, queryProcessor, ServerSocketFactory.getDefault());
    }

    public ZabbixServer(String datapath, BiConsumer<Socket, byte[]> queryProcessor, ServerSocketFactory socketFactory) {
        this.queryProcessor = queryProcessor;
        this.socketFactory = socketFactory;
        dataurl = getClass().getClassLoader().getResource(datapath);
        if (dataurl == null) {
            throw new IllegalArgumentException(datapath);
        }
        setUncaughtExceptionHandler(this);
        setName("ZabbixServer");
        start();
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = socketFactory.createServerSocket()){
            serverSocket.setSoTimeout(100);
            socketHolder.set(serverSocket);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            started.countDown();
            while (! serverSocket.isClosed()) {
                try (Socket client = serverSocket.accept();
                     ZabbixProtocol handler = new ZabbixProtocol(client)
                ) {
                    byte[] queryData = handler.read();
                    queryProcessor.accept(client, queryData);
                    try (InputStream datastream = dataurl.openStream()) {
                        client.getOutputStream().write(datastream.readAllBytes());
                    }
                } catch (SocketException | SocketTimeoutException ex) {
                    /* do loop */
                }
            }
            doneProcessing.complete(true);
        } catch (ClosedByInterruptException e) {
            doneProcessing.complete(true);
        } catch (IOException e) {
            doneProcessing.completeExceptionally(e);
        }
    }

    public boolean waitStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return started.await(timeout, unit);
    }

    public boolean waitStopped(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return doneProcessing.get(timeout, unit);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        doneProcessing.completeExceptionally(e);
    }

    @Override
    public void close() {
        try {
            socketHolder.updateAndGet(ss -> {
                try {
                    if (ss != null) {
                        ss.close();
                    }
                    return ss;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            interrupt();
            doneProcessing.get();
        } catch (InterruptedException | CancellationException | ExecutionException ex) {
            /* ignore */
        }
    }

    public SocketAddress getAddress() throws InterruptedException {
        started.await();
        return socketHolder.get().getLocalSocketAddress();
    }
}
