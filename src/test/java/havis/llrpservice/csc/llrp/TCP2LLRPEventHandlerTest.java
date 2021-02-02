package havis.llrpservice.csc.llrp;

import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataReceivedNotifyEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.csc.llrp.event.LLRPEvent;
import havis.llrpservice.data.message.GetSupportedVersion;
import havis.llrpservice.data.message.GetSupportedVersionResponse;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;
import havis.llrpservice.common.tcp.event.TCPChannelClosedEvent;
import havis.llrpservice.common.tcp.event.TCPChannelOpenedEvent;
import havis.llrpservice.common.tcp.event.TCPDataReceivedNotifyEvent;
import havis.llrpservice.common.tcp.event.TCPDataSentEvent;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class TCP2LLRPEventHandlerTest {

	class LLRPEventHandlerStub implements LLRPEventHandler {
		private List<LLRPEvent> events = new ArrayList<LLRPEvent>();

		List<LLRPEvent> getEvents() {
			return events;
		}

		@Override
		public void channelOpened(LLRPChannelOpenedEvent event) {
			events.add(event);
		}

		@Override
		public void dataSent(LLRPDataSentEvent event) {
			events.add(event);
		}

		@Override
		public void dataReceived(LLRPDataReceivedNotifyEvent event) {
			events.add(event);
		}

		@Override
		public void channelClosed(LLRPChannelClosedEvent event) {
			events.add(event);
		}
	};

	/**
	 * Send an TCP event {@link TCPChannelOpenedEvent}.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A LLRP event {@link LLRPChannelOpenedEvent} is sent.
	 * </ul>
	 * </p>
	 * 
	 * @param serverChannel
	 * @param channel
	 */
	@Test
	public void channelOpened(@Mocked ServerSocketChannel serverChannel,
			@Mocked SocketChannel channel) {
		LLRPEventHandlerStub llrpEventHandler = new LLRPEventHandlerStub();
		TCP2LLRPEventHandler tcpEventHandler = new TCP2LLRPEventHandler(
				llrpEventHandler);
		// set logging to debug level
		Logger origLog = Deencapsulation.getField(tcpEventHandler, "log");
		Deencapsulation.setField(tcpEventHandler, "log",
				Logger.getLogger("testing"));
		// Send an TCP open event.
		tcpEventHandler.channelOpened(new TCPChannelOpenedEvent(serverChannel,
				channel));
		// A LLRP event LLRPChannelOpenedEvent is sent.
		List<LLRPEvent> events = llrpEventHandler.getEvents();
		Assert.assertEquals(events.size(), 1);
		LLRPChannelOpenedEvent event = (LLRPChannelOpenedEvent) events.get(0);
		Assert.assertEquals(event.getServerChannel(), serverChannel);
		Assert.assertEquals(event.getChannel(), channel);
		
		Deencapsulation.setField(tcpEventHandler, "log", origLog);
	}

	/**
	 * Serialize a LLRP message and send a TCP event {@link TCPDataSentEvent}
	 * with the message data. </p>
	 * <p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A LLRP event {@link LLRPDataSentEvent} with the message identifier is
	 * sent.
	 * </ul>
	 * </p>
	 * 
	 * @param serverChannel
	 * @param channel
	 */
	public void dataSent1(@Mocked ServerSocketChannel serverChannel,
			@Mocked SocketChannel channel) {
		LLRPEventHandlerStub llrpEventHandler = new LLRPEventHandlerStub();
		TCP2LLRPEventHandler tcpEventHandler = new TCP2LLRPEventHandler(
				llrpEventHandler);
		// set logging to debug level
		Logger origLog = Deencapsulation.getField(tcpEventHandler, "log");
		Deencapsulation.setField(tcpEventHandler, "log",
				Logger.getLogger("testing"));
		// Serialize a LLRP message
		Long messageId = 3L;
		GetSupportedVersion gsv = new GetSupportedVersion(new MessageHeader(
				(byte) 0, ProtocolVersion.LLRP_V1_0_1, 3));
		ByteBufferSerializer serializer = new ByteBufferSerializer();
		ByteBuffer data = ByteBuffer
				.allocate(ByteBufferSerializer.MESSAGE_HEADER_LENGTH);
		serializer.serialize(gsv.getMessageHeader(), data);
		// Send a TCP event with the message data
		tcpEventHandler.dataSent(new TCPDataSentEvent(serverChannel, channel,
				data));
		// A LLRP event LLRPDataSentEvent with the message is sent
		List<LLRPEvent> events = llrpEventHandler.getEvents();
		Assert.assertEquals(events.size(), 1);
		LLRPDataSentEvent event = (LLRPDataSentEvent) events.get(0);
		Assert.assertEquals(event.getServerChannel(), serverChannel);
		Assert.assertEquals(event.getChannel(), channel);
		Assert.assertEquals(event.getMessageId(), messageId);
		Assert.assertNull(event.getPendingData());
		Assert.assertNull(event.getException());
		
		Deencapsulation.setField(tcpEventHandler, "log", origLog);
	}

	/**
	 * Send a TCP event {@link TCPDataSentEvent} with an invalid LLRP message.
	 * The LLRP deserialization fails.
	 * <p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A LLRP event {@link LLRPDataSentEvent} is sent containing all pending
	 * data.
	 * </ul>
	 * </p>
	 * 
	 * @param serverChannel
	 * @param channel
	 * @throws InvalidProtocolVersionException
	 */
	public void dataSentError1(@Mocked ServerSocketChannel serverChannel,
			@Mocked SocketChannel channel)
			throws InvalidParameterTypeException, InvalidMessageTypeException,
			InvalidProtocolVersionException {
		LLRPEventHandlerStub llrpEventHandler = new LLRPEventHandlerStub();
		TCP2LLRPEventHandler tcpEventHandler = new TCP2LLRPEventHandler(
				llrpEventHandler);
		// Send a TCP event with an invalid LLRP message.
		GetSupportedVersionResponse gsvr = new GetSupportedVersionResponse(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 0),
				ProtocolVersion.LLRP_V1_0_1, ProtocolVersion.LLRP_V1_1,
				new LLRPStatus(new TLVParameterHeader((byte) 0),
						LLRPStatusCode.A_OUT_OF_RANGE, "error"));
		final ByteBufferSerializer serializer = new ByteBufferSerializer();
		long messageLength = serializer.getLength(gsvr);
		ByteBuffer data = ByteBuffer.allocate((int) messageLength);
		serializer.serialize(gsvr, data);
		// The LLRP deserialization fails.
		final Throwable exception = new InvalidParameterTypeException("huhu");
		new NonStrictExpectations(ByteBufferSerializer.class) {
			{
				serializer
						.deserializeMessageHeader(withInstanceOf(ByteBuffer.class));
				result = exception;
			}
		};
		tcpEventHandler.dataSent(new TCPDataSentEvent(serverChannel, channel,
				data));
		// A LLRP event LLRPDataReceivedEvent is sent containing all pending
		// data.
		List<LLRPEvent> events = llrpEventHandler.getEvents();
		Assert.assertEquals(events.size(), 1);
		LLRPDataSentEvent event = (LLRPDataSentEvent) events.get(0);
		Assert.assertEquals(event.getServerChannel(), serverChannel);
		Assert.assertEquals(event.getChannel(), channel);
		Assert.assertEquals(event.getPendingData().remaining(), messageLength);
	}

	/**
	 * Send a TCP event {@link TCPDataReceivedNotifyEvent}.</p>
	 * <p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A LLRP event {@link LLRPDataReceivedNotifyEvent} is sent.
	 * </ul>
	 * </p>
	 * 
	 * @param serverChannel
	 * @param channel
	 */
	public void dataReceived1(@Mocked ServerSocketChannel serverChannel,
			@Mocked SocketChannel channel) {
		LLRPEventHandlerStub llrpEventHandler = new LLRPEventHandlerStub();
		TCP2LLRPEventHandler tcpEventHandler = new TCP2LLRPEventHandler(
				llrpEventHandler);
		// Send a TCP event TCPDataReceivedNotifyEvent
		tcpEventHandler.dataReceived(new TCPDataReceivedNotifyEvent(
				serverChannel, channel));
		// A LLRP event LLRPDataSentEvent is sent
		List<LLRPEvent> events = llrpEventHandler.getEvents();
		Assert.assertEquals(events.size(), 1);
		LLRPDataReceivedNotifyEvent event = (LLRPDataReceivedNotifyEvent) events
				.get(0);
		Assert.assertEquals(event.getServerChannel(), serverChannel);
		Assert.assertEquals(event.getChannel(), channel);
	}

	/**
	 * Send an TCP event {@link TCPChannelClosedEvent}.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A LLRP event {@link LLRPChannelClosedEvent} is sent.
	 * </ul>
	 * </p>
	 * 
	 * @param serverChannel
	 * @param channel
	 */
	public void channelClosed1(@Mocked ServerSocketChannel serverChannel,
			@Mocked SocketChannel channel) {
		LLRPEventHandlerStub llrpEventHandler = new LLRPEventHandlerStub();
		TCP2LLRPEventHandler tcpEventHandler = new TCP2LLRPEventHandler(
				llrpEventHandler);
		Logger origLog = Deencapsulation.getField(tcpEventHandler, "log");
		Deencapsulation.setField(tcpEventHandler, "log",
				Logger.getLogger("testing"));
		// Send an TCP event TCPChannelClosedEvent.
		ByteBuffer pendingSendingData = ByteBuffer.allocate(4);
		ByteBuffer pendingReceivedData = ByteBuffer.allocate(2);
		Throwable exception = new Exception();
		tcpEventHandler.channelClosed(new TCPChannelClosedEvent(serverChannel,
				channel, pendingSendingData, pendingReceivedData, exception));
		// The LLRP event LLRPChannelClosedEvent is sent.
		List<LLRPEvent> events = llrpEventHandler.getEvents();
		Assert.assertEquals(events.size(), 1);
		LLRPChannelClosedEvent event = (LLRPChannelClosedEvent) events.get(0);
		Assert.assertEquals(event.getServerChannel(), serverChannel);
		Assert.assertEquals(event.getChannel(), channel);
		Assert.assertEquals(event.getPendingSendingData(), pendingSendingData);
		Assert.assertEquals(event.getPendingReceivedData(), pendingReceivedData);
		Assert.assertEquals(event.getException(), exception);

		Deencapsulation.setField(tcpEventHandler, "log", origLog);
	}
}
