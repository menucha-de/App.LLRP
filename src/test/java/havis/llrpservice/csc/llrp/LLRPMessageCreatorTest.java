package havis.llrpservice.csc.llrp;

import havis.llrpservice.data.message.GetSupportedVersion;
import havis.llrpservice.data.message.GetSupportedVersionResponse;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.MessageTypes.MessageType;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import mockit.Deencapsulation;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class LLRPMessageCreatorTest {
	/**
	 * Serialize a LLRP message which only consists of a message header.
	 * <p>
	 * Append a part of the message data to the message creator.
	 * </p>
	 * <p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>No message is created.
	 * </ul>
	 * </p>
	 * <p>
	 * Append the rest of the message data.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A message is created.
	 * </ul>
	 * </p>
	 * 
	 * @param channel
	 */
	public void append1(@Mocked SocketChannel channel) throws Exception {
		LLRPMessageCreator messageCreator = new LLRPMessageCreator();
		ByteCollector collector = new ByteCollector();
		Deencapsulation.setField(messageCreator, "collector", collector);
		Map<SocketChannel, MessageHeader> messageHeaders = new HashMap<SocketChannel, MessageHeader>();
		Deencapsulation.setField(messageCreator, "messageHeaders",
				messageHeaders);
		// Serialize a LLRP message which only consists of a message header
		GetSupportedVersion gsv = new GetSupportedVersion(new MessageHeader(
				(byte) 0, ProtocolVersion.LLRP_V1_0_1, 1));
		ByteBufferSerializer serializer = new ByteBufferSerializer();
		ByteBuffer data = ByteBuffer.allocate((int) serializer.getLength(gsv));
		serializer.serialize(gsv, data);
		data.flip();
		// Append a part of the message data to the message creator.
		byte[] dst = new byte[4];
		data.get(dst);
		ByteBuffer dataPart = ByteBuffer.allocate(4);
		dataPart.put(dst);
		dataPart.flip();
		Message msg = messageCreator.append(channel, dataPart);
		// No message is created
		Assert.assertNull(msg);
		Assert.assertEquals(collector.size(), 1);
		Assert.assertTrue(messageHeaders.isEmpty());
		// Append the rest of the message data
		dst = new byte[6];
		data.get(dst);
		dataPart = ByteBuffer.allocate(6);
		dataPart.put(dst);
		dataPart.flip();
		msg = messageCreator.append(channel, dataPart);
		// A message is created.
		Assert.assertNotNull(msg);
		GetSupportedVersion gsvReceived = (GetSupportedVersion) msg;
		Assert.assertEquals(gsvReceived.getMessageHeader().getMessageType(),
				MessageType.GET_SUPPORTED_VERSION);
		Assert.assertEquals(collector.size(), 0);
		Assert.assertTrue(messageHeaders.isEmpty());
	}

	/**
	 * Append one byte buffer with multiple serialized messages containing
	 * message bodies to the message creator.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The first message is created and returned.
	 * </ul>
	 * </p>
	 * <p>
	 * Call {@link LLRPMessageCreator#append(SocketChannel, ByteBuffer)} with the
	 * same byte buffer multiple times until all messages are created.
	 * </p>
	 * 
	 * @param channel
	 */
	public void append2(@Mocked SocketChannel channel) throws Exception {
		LLRPMessageCreator messageCreator = new LLRPMessageCreator();
		ByteCollector collector = new ByteCollector();
		Deencapsulation.setField(messageCreator, "collector", collector);
		Map<SocketChannel, MessageHeader> messageHeaders = new HashMap<SocketChannel, MessageHeader>();
		Deencapsulation.setField(messageCreator, "messageHeaders",
				messageHeaders);
		// Append multiple serialized messages containing message bodies to the
		// message creator.
		GetSupportedVersionResponse gsvr = new GetSupportedVersionResponse(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 0),
				ProtocolVersion.LLRP_V1_0_1, ProtocolVersion.LLRP_V1_1,
				new LLRPStatus(new TLVParameterHeader((byte) 0),
						LLRPStatusCode.A_OUT_OF_RANGE, "error"));
		ByteBufferSerializer serializer = new ByteBufferSerializer();
		long messageLength = serializer.getLength(gsvr);
		int messageCount = 3;
		ByteBuffer data = ByteBuffer.allocate((int) messageLength
				* messageCount);
		for (int i = 0; i < messageCount; i++) {
			gsvr.getMessageHeader().setId(i);
			serializer.serialize(gsvr, data);
		}
		data.flip();
		// Call 'append' with the same byte buffer multiple times until all
		// messages are created.
		for (int i = 0; i < messageCount; i++) {
			Message msg = messageCreator.append(channel, data);
			Assert.assertEquals(msg.getMessageHeader().getId(), i);
			Assert.assertEquals(collector.size(), 0);
			Assert.assertTrue(messageHeaders.isEmpty());
		}
		Assert.assertEquals(data.remaining(), 0);
	}

	/**
	 * Append a LLRP message with an invalid header to the message creator. The
	 * deserialization fails.
	 * <p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An exception is thrown.
	 * </ul>
	 * </p>
	 * Remove the pending data from the message creator.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The data of the message header is returned.
	 * </ul>
	 * </p>
	 * 
	 * @param channel
	 */
	public void appendError1(@Mocked SocketChannel channel) throws Exception {
		LLRPMessageCreator tcpEventHandler = new LLRPMessageCreator();
		ByteCollector collector = new ByteCollector();
		Deencapsulation.setField(tcpEventHandler, "collector", collector);
		Map<SocketChannel, MessageHeader> messageHeaders = new HashMap<SocketChannel, MessageHeader>();
		Deencapsulation.setField(tcpEventHandler, "messageHeaders",
				messageHeaders);
		// Append a LLRP message with an invalid header to the message creator.
		GetSupportedVersionResponse gsvr = new GetSupportedVersionResponse(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 5),
				ProtocolVersion.LLRP_V1_0_1, ProtocolVersion.LLRP_V1_1,
				new LLRPStatus(new TLVParameterHeader((byte) 0),
						LLRPStatusCode.A_OUT_OF_RANGE, "error"));
		final ByteBufferSerializer serializer = new ByteBufferSerializer();
		long messageLength = serializer.getLength(gsvr);
		ByteBuffer data = ByteBuffer.allocate((int) messageLength);
		serializer.serialize(gsvr, data);
		data.flip();
		// The deserialization fails.
		data.putShort((short) 0);
		data.rewind();
		try {
			tcpEventHandler.append(channel, data);
			Assert.fail();
		} catch (InvalidProtocolVersionException e) {
			Assert.assertTrue(e.getMessage().contains(
					"Invalid protocol version 0"));
		} catch (Throwable t) {
			Assert.fail();
		}
		// The message header has been read before the deserialization.
		Assert.assertEquals(data.position(),
				ByteBufferSerializer.MESSAGE_HEADER_LENGTH);
		// Get the read data.
		ByteBuffer removedData = tcpEventHandler.remove(channel);
		Assert.assertEquals(removedData.remaining(),
				ByteBufferSerializer.MESSAGE_HEADER_LENGTH);
		Assert.assertEquals(collector.size(), 0);
		Assert.assertTrue(messageHeaders.isEmpty());
	}

	/**
	 * Append a LLRP message with an invalid body to the message creator. The
	 * deserialization fails.
	 * <p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An exception is thrown.
	 * </ul>
	 * </p>
	 * Remove the pending data from the message creator.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The whole message data are returned.
	 * </ul>
	 * </p>
	 * 
	 * @param channel
	 */
	public void appendError2(@Mocked SocketChannel channel) throws Exception {
		LLRPMessageCreator tcpEventHandler = new LLRPMessageCreator();
		ByteCollector collector = new ByteCollector();
		Deencapsulation.setField(tcpEventHandler, "collector", collector);
		Map<SocketChannel, MessageHeader> messageHeaders = new HashMap<SocketChannel, MessageHeader>();
		Deencapsulation.setField(tcpEventHandler, "messageHeaders",
				messageHeaders);
		// Append a LLRP message with an invalid header to the message creator.
		GetSupportedVersionResponse gsvr = new GetSupportedVersionResponse(
				new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 5),
				ProtocolVersion.LLRP_V1_0_1, ProtocolVersion.LLRP_V1_1,
				new LLRPStatus(new TLVParameterHeader((byte) 0),
						LLRPStatusCode.A_OUT_OF_RANGE, "error"));
		final ByteBufferSerializer serializer = new ByteBufferSerializer();
		long messageLength = serializer.getLength(gsvr);
		ByteBuffer data = ByteBuffer.allocate((int) messageLength);
		serializer.serialize(gsvr, data);
		data.flip();
		// The deserialization fails.
		final InvalidParameterTypeException exception = new InvalidParameterTypeException(
				"huhu");
		new NonStrictExpectations(ByteBufferSerializer.class) {
			{
				serializer.deserializeMessage(
						withInstanceOf(MessageHeader.class),
						withInstanceOf(ByteBuffer.class));
				result = exception;
			}
		};
		try {
			tcpEventHandler.append(channel, data);
			Assert.fail();
		} catch (InvalidParameterTypeException e) {
		} catch (Throwable t) {
			Assert.fail();
		}
		// The whole message has been read before the deserialization.
		Assert.assertEquals(data.position(), serializer.getLength(gsvr));
		// The whole message data are returned.
		ByteBuffer removedData = tcpEventHandler.remove(channel);
		Assert.assertEquals(removedData.remaining(), serializer.getLength(gsvr));
		Assert.assertEquals(collector.size(), 0);
		Assert.assertTrue(messageHeaders.isEmpty());
	}
}
