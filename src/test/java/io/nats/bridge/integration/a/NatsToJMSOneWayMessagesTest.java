package io.nats.bridge.integration.a;

import io.nats.bridge.messages.Message;
import io.nats.bridge.MessageBridge;
import io.nats.bridge.MessageBus;
import io.nats.bridge.messages.StringMessage;
import io.nats.bridge.integration.TestUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class NatsToJMSOneWayMessagesTest {

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

    @Before
    public void setUp() throws Exception {

        final String busName = "MessagesOnlyA";
        final String responseName = "RESPONSEA";
        clientMessageBus = TestUtils.getMessageBusNats(busName);
        serverMessageBus = TestUtils.getMessageBusJms(busName);
        resultSignal = new CountDownLatch(1);
        serverStopped = new CountDownLatch(1);
        bridgeStopped = new CountDownLatch(1);

        bridgeMessageBusSource = TestUtils.getMessageBusNats(busName);
        bridgeMessageBusDestination = TestUtils.getMessageBusJms(busName);

        responseBusServer = TestUtils.getMessageBusJms(responseName);
        responseBusClient = TestUtils.getMessageBusJms(responseName);
        messageBridge = new MessageBridge(bridgeMessageBusSource, bridgeMessageBusDestination, false);

    }

    @Test
    public void test() throws Exception {
        runServerLoop();
        runBridgeLoop();
        runClientLoop();
        clientMessageBus.publish("Rick");
        resultSignal.await(10, TimeUnit.SECONDS);

        assertEquals("Hello Rick", responseFromServer.get());
        stopServerAndBridgeLoops();
    }

    private void runClientLoop() {

        Thread th = new Thread(() -> {

            Optional<Message> receive;
            while (true) {
                receive = responseBusClient.receive();
                if (receive.isPresent()) {
                    Message message = receive.get();
                    responseFromServer.set(((StringMessage) message).getBody());
                    resultSignal.countDown();
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        th.start();
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


    public static void runServerLoop(final AtomicBoolean stop, final MessageBus serverMessageBus, final MessageBus responseBusServer,
                                     final CountDownLatch serverStopped) {
        final Thread thread = new Thread(() -> {
            while (true) {
                if (stop.get()) {
                    serverMessageBus.close();
                    break;
                }
                final Optional<Message> receive = serverMessageBus.receive();
                receive.ifPresent(message -> {
                    StringMessage stringMessage = (StringMessage) message;
                    System.out.println("Handle message " + stringMessage.getBody());
                    responseBusServer.publish(new StringMessage("Hello " + stringMessage.getBody()));
                });
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                serverMessageBus.process();
            }
            serverStopped.countDown();
        });
        thread.start();

    }
}