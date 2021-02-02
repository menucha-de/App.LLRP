package havis.llrpservice.server.llrp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.Test;

import havis.llrpservice.csc.llrp.LLRPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPTimeoutException;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.ErrorMessage;
import havis.llrpservice.data.message.KeepaliveAck;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.MessageTypes.MessageType;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.parameter.ConnectionAttemptEventStatusType;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationData;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;
import havis.util.platform.Platform;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

public class LLRPConnectionHandlerTest {

	// @Mocked
	// LLRPServerMultiplexed llrpServer;
	// @Mocked
	// LLRPServerEventHandler serverEventHandler;
	// @Mocked
	// SocketChannel clientChannel;
	// @Mocked
	// LLRPDataSentEvent event;
	// @Mocked
	// LLRPMessageHandlerListener listener;
	// @Mocked
	// Platform platform;

	@Test
	public void handling(//
			@Mocked final LLRPServerMultiplexed llrpServer,
			@Mocked final LLRPServerEventHandler serverEventHandler,
			@Mocked final SocketChannel clientChannel, @Mocked final LLRPDataSentEvent event,
			@Mocked final LLRPMessageHandlerListener listener, @Mocked final Platform platform//
	) throws Throwable {
		class Data {
			boolean hasUTCClock = false;
		}
		final Data data = new Data();
		new Expectations() {
			int counter = 0;
			{
				serverEventHandler.getLLRPServer();
				result = llrpServer;

				llrpServer.awaitReceivedData(clientChannel, anyLong);
				result = new Delegate<LLRPServerMultiplexed>() {
					@SuppressWarnings("unused")
					Message awaitReceivedData(SocketChannel clientChannel, long timeout)
							throws LLRPTimeoutException, LLRPUnknownChannelException,
							InvalidProtocolVersionException, InvalidMessageTypeException,
							InvalidParameterTypeException {
						switch (++counter) {
						case 1:
							return new ReaderEventNotification(
									new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 123),
									new ReaderEventNotificationData());
						case 2:
							throw new LLRPTimeoutException("timeout");
						case 3:
							throw new InvalidProtocolVersionException("protocol");
						case 4:
							throw new InvalidMessageTypeException("messageType");
						case 5:
							throw new InvalidParameterTypeException("parameterType");
						case 6:
							throw new LLRPUnknownChannelException("any exception");
						case 7:
							return new KeepaliveAck(
									new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_1, 456));
						}
						return null;
					}
				};

				platform.hasUTCClock();
				result = new Delegate<Platform>() {
					@SuppressWarnings("unused")
					boolean hasUTCCLock() {
						return data.hasUTCClock;
					}
				};
			}
		};
		List<LLRPMessageHandlerListener> listeners = new ArrayList<>();
		// Create a LLRPConnectionHandler
		final LLRPConnectionHandler handler = new LLRPConnectionHandler(serverEventHandler,
				clientChannel, platform, listeners);
		// clientChannel should be equal to clientChannel of handler
		assertEquals(clientChannel, handler.getChannel());
		handler.addListener(listener);
		// send a connectionDenied message WITHOUT UTC time stamp and confirm
		// the sending via dataSent method (listeners are not informed)
		handler.requestSendingConnectionDeniedEvent();
		handler.dataSent(event);

		// Create another LLRPConnectionHandler
		data.hasUTCClock = true;
		LLRPConnectionHandler handlerWithUTC = new LLRPConnectionHandler(serverEventHandler,
				clientChannel, platform, listeners);
		// send a connectionDenied message WITH UTC time stamp and confirm the
		// sending via dataSent method (listeners are not informed)
		handlerWithUTC.requestSendingConnectionDeniedEvent();
		handlerWithUTC.dataSent(event);
		data.hasUTCClock = false;

		// send a connectionAttemptedSuccess message without UTC Timestamp and
		// confirm the sending via dataSent method (listeners are not informed)
		handler.requestSendingConnectionAcceptedEvent();
		handler.dataSent(event);

		final List<Message> sendMessages = new ArrayList<>();
		new Verifications() {
			{
				llrpServer.requestSendingData(clientChannel, withCapture(sendMessages));
				times = 3;

				listener.dataSent(event);
				times = 0;
			}
		};

		// verify the sent messages
		List<ConnectionAttemptEventStatusType> connectionAttemptStatus = Arrays.asList(
				ConnectionAttemptEventStatusType.FAILED_CLIENT_CONNECTION_EXISTS,
				ConnectionAttemptEventStatusType.FAILED_CLIENT_CONNECTION_EXISTS,
				ConnectionAttemptEventStatusType.SUCCESS);
		for (int i = 0; i < sendMessages.size(); i++) {
			ReaderEventNotification notify = (ReaderEventNotification) sendMessages.get(i);
			// verify sent status
			assertEquals(
					notify.getReaderEventNotificationData().getConnectionAttemptEvent().getStatus(),
					connectionAttemptStatus.get(i));
			// verify UTC Timestamp (only second message has a UTC Timestamp)
			if (i == 1) {
				assertNotNull(notify.getReaderEventNotificationData().getUtcTimestamp());
			} else {
				assertNull(notify.getReaderEventNotificationData().getUtcTimestamp());
			}
		}

		// confirm the sending of any data (the event is forwarded to the
		// listener)
		handler.dataSent(event);

		new Verifications() {
			{
				listener.dataSent(event);
				times = 1;
			}
		};

		// remove the listener and confirm the sending of any data again
		// (the listener is not informed)
		handler.removeListener(listener);
		handler.dataSent(event);

		new Verifications() {
			{
				listener.dataSent(event);
				times = 1;
			}
		};

		// Call the awaitReceivedData method. The internal awaitReceivedData
		// call in this method is mocked as described in expectations.
		// get first message
		assertEquals(handler.awaitReceivedData().getMessageHeader().getId(), 123);
		try {
			// get exceptions: LLRPTimeoutException,
			// InvalidProtocolTypeException, InvalidMessageTypeException,
			// InvalidParameterTypeException + "any exception"
			handler.awaitReceivedData();
			fail();
		} catch (LLRPUnknownChannelException e) {
			assertTrue(e.getMessage().equals("any exception"));
		}
		new Verifications() {
			{
				// 3 error messages has been sent (LLRPTimeoutException has been
				// ignored)
				llrpServer.requestSendingData(clientChannel, withCapture(sendMessages));
				times = 6;
			}
		};
		// verify error messages
		List<LLRPStatusCode> statusCodes = Arrays.asList(LLRPStatusCode.M_UNSUPPORTED_VERSION,
				LLRPStatusCode.M_UNSUPPORTED_MESSAGE, LLRPStatusCode.M_PARAMETER_ERROR);
		int errorMsgCount = 0;
		for (Message msg : sendMessages) {
			if (msg.getMessageHeader().getMessageType() == MessageType.ERROR_MESSAGE) {
				ErrorMessage errorMsg = (ErrorMessage) msg;
				assertEquals(errorMsg.getStatus().getStatusCode(), statusCodes.get(errorMsgCount));
				errorMsgCount++;
			}
		}
		assertEquals(errorMsgCount, 3);

		// start keepalive
		handler.setKeepalive(300 /* interval */, 3000 /* unexpectedTimeout */);
		Thread.sleep(500);
		// send keep alive acknowlegde for first keep alive message
		assertEquals(handler.awaitReceivedData().getMessageHeader().getId(), 456);
		Thread.sleep(300);
		// do not send keep alive acknowlegde for second keep alive message
		assertNull(handler.awaitReceivedData());
		Thread.sleep(300);
		// instead of sending a third keep alive message the execution is
		// stopped
		handler.awaitReceivedData();
		new Verifications() {
			{
				// 2 keep alive messages has been sent
				llrpServer.requestSendingData(clientChannel, withCapture(sendMessages));
				times = 8;

				// the execution has been aborted
				serverEventHandler.abortExecution(withInstanceOf(Exception.class));
				times = 1;
			}
		};
		// verify sent messages
		int keepaliveCount = 0;
		for (Message msg : sendMessages) {
			if (msg.getMessageHeader().getMessageType() == MessageType.KEEPALIVE) {
				keepaliveCount++;
			}
		}
		assertEquals(keepaliveCount, 2);

		// stop keepalive
		handler.setKeepalive(0/* interval */, 3000 /* unexpectedTimeout */);
		Thread.sleep(700);
		new Verifications() {
			{
				// no additional keep alive messages have been sent
				llrpServer.requestSendingData(clientChannel, withInstanceOf(Message.class));
				times = 8;
			}
		};

	}
}
