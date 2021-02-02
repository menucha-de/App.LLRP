package havis.llrpservice.server.llrp;

import havis.llrpservice.csc.llrp.LLRPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationData;
import havis.util.platform.Platform;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LLRPServerEventHandlerTest {
	private final long timeout = 1000;

	@Test
	public void serverHandling(@Mocked final LLRPServerMultiplexed llrpServer,
			@Mocked Platform systemController) throws Throwable {
		final ServerSocketChannel serverChannel = ServerSocketChannel.open();
		final LLRPChannelOpenedEvent openedEvent = new LLRPChannelOpenedEvent(serverChannel,
				null /* clientChannel */);
		LLRPChannelClosedEvent closedEvent = new LLRPChannelClosedEvent(serverChannel,
				null /* clientChannel */, null /* pendingSendingData */,
				null /* pendingReceivedData */, null /* exception */);

		final LLRPServerEventHandler handler = new LLRPServerEventHandler(llrpServer,
				systemController, new ArrayList<LLRPMessageHandlerListener>());

		ExecutorService threads = Executors.newFixedThreadPool(1);
		// Wait for server opening WITHOUT confirming the sending of the opened
		// event
		Future<ServerSocketChannel> future = threads.submit(new Callable<ServerSocketChannel>() {
			@Override
			public ServerSocketChannel call() throws Exception {
				return handler.awaitServerOpening(timeout);
			}
		});
		long startTime = System.currentTimeMillis();
		try {
			future.get(3000, TimeUnit.MILLISECONDS);
			Assert.fail();
		} catch (Exception e) {
			Assert.assertTrue(e.getCause() instanceof TimeoutException
					&& (System.currentTimeMillis() - startTime) < 3000);
		}

		// Wait for server opening WITH confirming the sending of the opened
		// event
		future = threads.submit(new Callable<ServerSocketChannel>() {
			@Override
			public ServerSocketChannel call() throws Exception {
				return handler.awaitServerOpening(timeout);
			}
		});
		handler.channelOpened(openedEvent);
		Assert.assertEquals(future.get(3000, TimeUnit.MILLISECONDS),
				openedEvent.getServerChannel());

		// Wait for server closing WITHOUT confirming the sending of the closed
		// event
		Future<Object> closeFuture = threads.submit(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				handler.awaitServerClosing(timeout);
				return null;
			}
		});
		startTime = System.currentTimeMillis();
		try {
			closeFuture.get(3000, TimeUnit.MILLISECONDS);
			Assert.fail();
		} catch (Exception e) {
			Assert.assertTrue(e.getCause() instanceof TimeoutException
					&& (System.currentTimeMillis() - startTime) < 3000);
		}

		// Wait for server closing WITH confirming the sending of the closed
		// event
		closeFuture = threads.submit(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				handler.awaitServerClosing(timeout);
				return null;
			}
		});
		handler.channelClosed(closedEvent);
		closeFuture.get(3000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void clientHandling(@Mocked final LLRPConnectionHandler connectionHandler,
			@Mocked final LLRPServerMultiplexed llrpServer,
			@Mocked final LLRPMessageHandlerListener listener,
			@Mocked final Platform systemController) throws Throwable {
		final ServerSocketChannel serverChannel = ServerSocketChannel.open();
		final SocketChannel clientChannel = SocketChannel.open();
		final LLRPChannelOpenedEvent openedEvent = new LLRPChannelOpenedEvent(serverChannel,
				clientChannel);
		final SocketChannel clientChannelDenied = SocketChannel.open();
		final LLRPChannelOpenedEvent openedEventDenied = new LLRPChannelOpenedEvent(serverChannel,
				clientChannelDenied);
		final LLRPChannelClosedEvent closedEvent = new LLRPChannelClosedEvent(serverChannel,
				clientChannel, null /* pendingSendingData */, null /* pendingReceivedData */,
				null /* exception */);
		final LLRPDataSentEvent sentEvent = new LLRPDataSentEvent(serverChannel, clientChannel,
				1L /* messageId */, null /* pendingData */, null /* exception */);
		// The awaitReceivedData method of the connection handler should throw
		// an exception to simulate a closed connection (connection will be
		// closed with an exception)
		new NonStrictExpectations() {
			{
				connectionHandler.getChannel();
				result = clientChannel;

				connectionHandler.awaitReceivedData();
				result = new LLRPUnknownChannelException("Any Exception");
			}
		};

		// Create a new client event handler
		final LLRPServerEventHandler handler = new LLRPServerEventHandler(llrpServer,
				systemController, new ArrayList<LLRPMessageHandlerListener>());

		ExecutorService threads = Executors.newFixedThreadPool(1);
		// Add a listener without having an established connection
		handler.addListener(listener);
		new Verifications() {
			{
				connectionHandler.addListener(listener);
				times = 0;
			}
		};

		// Open a client handler channel and confirm the sending of acceptance
		// message
		handler.channelOpened(openedEvent);
		handler.dataSent(sentEvent);

		// A connection attempted success has been sent
		new Verifications() {
			{
				connectionHandler.requestSendingConnectionAcceptedEvent();
				times = 1;
				connectionHandler.dataSent(sentEvent);
				times = 1;
			}
		};

		// Add and remove listener to connection handler and check, if it is put
		// through
		handler.addListener(listener);
		handler.removeListener(listener);
		new Verifications() {
			{
				connectionHandler.addListener(listener);
				times = 1;
				connectionHandler.removeListener(listener);
				times = 1;
			}
		};

		// add listener again
		handler.addListener(listener);

		// Try to open a further channel: the connection is denied and closed;
		// a "dataSent" event is ignored
		handler.channelOpened(openedEventDenied);
		new Verifications() {
			{
				connectionHandler.requestSendingConnectionDeniedEvent();
				times = 1;
				connectionHandler.dataSent(sentEvent);
				times = 1;
				llrpServer.requestClosingChannel(clientChannelDenied, false /* force */);
				times = 1;
			}
		};

		// Send data via the valid connection and confirm its sending
		handler.requestSendingData(new ReaderEventNotification(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 0),
				new ReaderEventNotificationData()));
		handler.dataSent(sentEvent);
		new Verifications() {
			{
				connectionHandler.requestSendingData(withInstanceOf(Message.class));
				times = 1;
				connectionHandler.dataSent(sentEvent);
				times = 2;
			}
		};

		// Close the channel from remote side
		// A "closed" event is sent to the listener
		handler.channelClosed(closedEvent);
		new Verifications() {
			{
				listener.closed(closedEvent);
				times = 1;
			}
		};

		// Open a connection again and confirm sending of acceptance message
		handler.channelOpened(openedEvent);
		handler.dataSent(sentEvent);
		new Verifications() {
			{
				connectionHandler.requestSendingConnectionAcceptedEvent();
				times = 2;
				connectionHandler.dataSent(sentEvent);
				times = 3;
			}
		};

		// Start waiting for messages via the client handler
		Future<Object> future = threads.submit(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				handler.awaitReceivedData();
				return null;
			}
		});

		// The connection has been opened and awaitReceivedData of connection
		// handler throws an LLRPUnknownChannelException => the thread is closed
		// directly
		try {
			future.get(timeout, TimeUnit.MILLISECONDS);
			Assert.fail();
		} catch (Exception e) {
			Assert.assertTrue(e.getMessage().contains("Any Exception"));
		}
		// confirm the closing of the client channel
		handler.channelClosed(closedEvent);
		new Verifications() {
			{
				listener.closed(closedEvent);
				times = 2;
			}
		};

		// Start waiting for a client via the client handler
		future = threads.submit(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				handler.awaitReceivedData();
				return null;
			}
		});

		// Cancel the execution of the event handler
		handler.cancelExecution();
		new Verifications() {
			{
				// no open channel => no channel must be to be closed
				llrpServer.requestClosingChannel(clientChannel, false /* force */);
				times = 0;
			}
		};

		// the thread is closed without an exception
		future.get(timeout, TimeUnit.MILLISECONDS);
		// Remove listener
		handler.removeListener(listener);
		new Verifications() {
			{
				connectionHandler.removeListener(listener);
				times = 1;
			}
		};

	}
}
