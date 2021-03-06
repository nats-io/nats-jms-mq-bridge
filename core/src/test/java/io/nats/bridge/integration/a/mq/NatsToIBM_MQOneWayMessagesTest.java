package io.nats.bridge.integration.a.mq;

import io.nats.bridge.MessageBridge;
import io.nats.bridge.MessageBus;
import io.nats.bridge.TestUtils;
import io.nats.bridge.messages.Message;
import io.nats.bridge.messages.MessageBuilder;
import io.nats.bridge.support.MessageBridgeForward;
import io.nats.bridge.support.MessageBridgeRequestReply;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class NatsToIBM_MQOneWayMessagesTest {

    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicReference<String> responseFromServer = new AtomicReference<>();
    private CountDownLatch resultSignal;
    private CountDownLatch serverStopped;
    private CountDownLatch bridgeStopped;

    private MessageBus serverMessageBus;
    private MessageBus clientMessageBus;
    private MessageBus bridgeMessageBusSource;
    private MessageBus bridgeMessageBusDestination;

    private MessageBus responseBusServer;
    private MessageBus responseBusClient;
    private MessageBridge messageBridge;

    public static void runServerLoop(final AtomicBoolean stop, final MessageBus serverMessageBus, final MessageBus responseBusServer,
                                     final CountDownLatch serverStopped) {
        final Thread thread = new Thread(() -> {
            while (true) {
                if (stop.get()) {
                    serverMessageBus.close();
                    break;
                }
                final Optional<Message> receive = serverMessageBus.receive();

                if (!receive.isPresent()) {
                }
                receive.ifPresent(message -> {

                    System.out.println("Handle message " + message.bodyAsString() + "....................");
                    responseBusServer.publish(MessageBuilder.builder().withBody("Hello " + message.bodyAsString()).build());

                });
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                serverMessageBus.process();
            }
            serverStopped.countDown();
        });
        thread.start();

    }

    @Before
    public void setUp() throws Exception {

        final String busName = "MessagesOnlyA";
        final String responseName = "RESPONSEA";
        clientMessageBus = TestUtils.getMessageBusNats("CLIENT", busName);
        serverMessageBus = TestUtils.getMessageBusIbmMQ("SERVER", true);
        resultSignal = new CountDownLatch(1);
        serverStopped = new CountDownLatch(1);
        bridgeStopped = new CountDownLatch(1);

        bridgeMessageBusSource = TestUtils.getMessageBusNats("BRIDGE_SOURCE", busName);
        bridgeMessageBusDestination = TestUtils.getMessageBusIbmMQ("BRIDGE_DEST", false);

        responseBusServer = TestUtils.getMessageBusJms("SERVER_RESPONSE", responseName);
        responseBusClient = TestUtils.getMessageBusJms("CLIENT_RESPONSE", responseName);
        messageBridge = new MessageBridgeForward("", bridgeMessageBusSource, bridgeMessageBusDestination,
                  Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap());

    }

    @Test
    public void test() throws Exception {
        TestUtils.drainBus(serverMessageBus);
        drainClientLoop();
        runServerLoop();
        runBridgeLoop();
        runClientLoop();
        clientMessageBus.publish("Rick");
        resultSignal.await(10, TimeUnit.SECONDS);

        for (int index = 0; index < 20; index++) {
            resultSignal.await(1, TimeUnit.SECONDS);
            if (responseFromServer.get() != null) break;
        }

        resultSignal.await(10, TimeUnit.SECONDS);

        assertEquals("Hello Rick", responseFromServer.get());
        stopServerAndBridgeLoops();
    }

    private void runClientLoop() throws Exception {

        Thread th = new Thread(() -> {

            Optional<Message> receive;
            while (true) {
                receive = responseBusClient.receive();
                if (!receive.isPresent()) {
                    //System.out.println("No Client Message");
                }
                if (receive.isPresent()) {
                    Message message = receive.get();
                    responseFromServer.set(message.bodyAsString());
                    resultSignal.countDown();
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        th.start();
        Thread.sleep(1000);
    }


    private void drainClientLoop() throws Exception {
        TestUtils.drainBus(clientMessageBus);
    }


    private void runBridgeLoop() {
        TestUtils.runBridgeLoop(messageBridge, stop, bridgeStopped);
    }

    private void stopServerAndBridgeLoops() throws Exception {
        TestUtils.stopServerAndBridgeLoops(stop, serverStopped, bridgeStopped);
    }

    private void runServerLoop() {
        runServerLoop(stop, serverMessageBus, responseBusServer, serverStopped);
    }
}
