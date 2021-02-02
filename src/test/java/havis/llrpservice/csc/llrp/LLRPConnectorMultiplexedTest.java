package havis.llrpservice.csc.llrp;

import havis.llrpservice.common.tcp.TCPClientMultiplexed;
import havis.llrpservice.common.tcp.TCPConnectorStoppedException;
import havis.llrpservice.common.tcp.TCPEventHandler;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.common.tcp.TCPTimeoutException;
import havis.llrpservice.common.tcp.TCPUnknownChannelException;
import havis.llrpservice.common.tcp.event.TCPChannelClosedEvent;
import havis.llrpservice.common.tcp.event.TCPChannelOpenedEvent;
import havis.llrpservice.common.tcp.event.TCPDataReceivedNotifyEvent;
import havis.llrpservice.common.tcp.event.TCPDataSentEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataReceivedNotifyEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.MessageTypes.MessageType;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.SetProtocolVersion;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.Test;

import test._EnvTest;

@Test
public class LLRPConnectorMultiplexedTest {

	/**
	 * Create a LLRP client and open a channel.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The LLRP event handler receives an opening event.
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param channel
	 * @param llrpEventHandler
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	public void requestOpeningChannel1(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final SocketChannel channel, @Mocked final LLRPEventHandler llrpEventHandler)
			throws UnknownHostException, IOException, TCPUnknownChannelException,
			TCPConnectorStoppedException {
		final String host = InetAddress.getLocalHost().getHostName();
		new NonStrictExpectations() {
			{
				tcpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1,
						withInstanceOf(TCPEventHandler.class));
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestOpeningChannel(String host, int port,
							TCPEventHandler eventHandler)
							throws IOException, TCPUnknownChannelException {
						eventHandler.channelOpened(new TCPChannelOpenedEvent(null, channel));
					}
				};
			}
		};
		// Create a LLRP client and open a channel.
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		llrpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1, llrpEventHandler);
		new Verifications() {
			{
				// The LLRP event handler receives an opening event.
				LLRPChannelOpenedEvent event;
				llrpEventHandler.channelOpened(event = withCapture());
				times = 1;
				Assert.assertEquals(event.getChannel(), channel);
			}
		};
	}

	/**
	 * Create a LLRP client and open a channel.
	 * <p>
	 * Send a LLRP message.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The LLRP client receives a {@link LLRPDataSentEvent} with the
	 * identifier of the sent message.
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param channel
	 * @param llrpEventHandler
	 * @throws Exception
	 */
	public void requestSendingData1(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final SocketChannel channel, @Mocked final LLRPEventHandler llrpEventHandler)
			throws Exception {
		class TCPClientData {
			TCPEventHandler eventHandler;
		}
		final TCPClientData tcpClientData = new TCPClientData();
		new NonStrictExpectations() {
			{
				tcpClient.requestOpeningChannel(anyString, anyInt,
						withInstanceOf(TCPEventHandler.class));
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestOpeningChannel(String host, int port,
							TCPEventHandler eventHandler)
							throws IOException, TCPUnknownChannelException {
						tcpClientData.eventHandler = eventHandler;
					}
				};

				tcpClient.requestSendingData(withInstanceOf(SocketChannel.class),
						withInstanceOf(ByteBuffer.class));
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestSendingData(SocketChannel channel, ByteBuffer data)
							throws TCPUnknownChannelException, TCPConnectorStoppedException {
						// "write" the data
						data.position(data.limit());
						// send a TCPDataSentEvent
						tcpClientData.eventHandler
								.dataSent(new TCPDataSentEvent(null, channel, data));
					}
				};
			}
		};
		// Create a LLRP client and open a channel
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		// set logging to debug level
		Logger origLog = Deencapsulation.getField(llrpClient, "log");
		Deencapsulation.setField(llrpClient, "log", Logger.getLogger("testing"));
		// Send a LLRP message.
		llrpClient.requestOpeningChannel(InetAddress.getLocalHost().getHostName(),
				_EnvTest.SERVER_PORT_1, llrpEventHandler);
		llrpClient.requestSendingData(channel,
				new SetProtocolVersion(new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 1),
						ProtocolVersion.LLRP_V1_0_1));

		new Verifications() {
			{
				// the TCP client receives the serialized LLRP message
				ByteBuffer data;
				tcpClient.requestSendingData(channel, data = withCapture());
				times = 1;
				data.rewind();
				ByteBufferSerializer serializer = new ByteBufferSerializer();
				MessageHeader header = serializer.deserializeMessageHeader(data);
				Message msg = serializer.deserializeMessage(header, data);
				Assert.assertEquals(msg.getMessageHeader().getMessageType(),
						MessageType.SET_PROTOCOL_VERSION);

				// The LLRP client receives a LLRPDataSentEvent with the
				// identifier of the sent message.
				LLRPDataSentEvent event;
				llrpEventHandler.dataSent(event = withCapture());
				times = 1;
				Assert.assertEquals(event.getMessageId().longValue(), 1L);
			}
		};

		Deencapsulation.setField(llrpClient, "log", origLog);
	}

	/**
	 * Create a LLRP client and open a channel.
	 * <p>
	 * Send a LLRP message. The TCP client throws an
	 * {@link TCPUnknownChannelException}.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>A {@link LLRPUnknownChannelException} is thrown.
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param channel
	 * @param llrpEventHandler
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InvalidMessageTypeException
	 * @throws InvalidProtocolVersionException
	 * @throws InvalidParameterTypeException
	 */
	public void requestSendingDataError1(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final SocketChannel channel, @Mocked final LLRPEventHandler llrpEventHandler)
			throws Exception {
		// The TCP client throws an TCPUnknownChannelException
		new NonStrictExpectations() {
			{
				tcpClient.requestSendingData(channel, withInstanceOf(ByteBuffer.class));
				result = new TCPUnknownChannelException("huhu");
			}
		};
		// Create a LLRP client and open a channel
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		// Send a LLRP message.
		try {
			llrpClient.requestSendingData(channel,
					new SetProtocolVersion(
							new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 1),
							ProtocolVersion.LLRP_V1_0_1));
			Assert.fail();
		} catch (LLRPUnknownChannelException e) {
			// A LLRPUnknownChannelException is thrown.
		} catch (Throwable t) {
			Assert.fail();
		}
	}

	/**
	 * Create a LLRP client and open a channel.
	 * <p>
	 * Await a LLRP message from a LLRP server.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The LLRP client receives a LLRP event
	 * {@link LLRPDataReceivedNotifyEvent}.
	 * <li>A message which has been sent from the LLRP server is received.
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param llrpEventHandler
	 */
	public void awaitReceivedData(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final LLRPEventHandler llrpEventHandler) throws Exception {
		final SocketChannel channel = SocketChannel.open();
		class Data {
			TCPEventHandler eventHandler;
		}
		final Data data = new Data();
		final String host = InetAddress.getLocalHost().getHostName();
		new NonStrictExpectations() {
			{
				tcpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1,
						withInstanceOf(TCPEventHandler.class));
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					void requestOpeningChannel(String host, int port, TCPEventHandler eventHandler)
							throws IOException, TCPUnknownChannelException {
						data.eventHandler = eventHandler;
						eventHandler.channelOpened(new TCPChannelOpenedEvent(null, channel));
					}
				};

				tcpClient.awaitReceivedData(channel, anyLong);
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					List<ByteBuffer> awaitReceivedData(SocketChannel channel, long timeout) {
						// create TCP message data
						Message msg = new SetProtocolVersion(
								new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 1),
								ProtocolVersion.LLRP_V1_0_1);
						ByteBufferSerializer serializer = new ByteBufferSerializer();
						ByteBuffer receivedData = null;
						try {
							receivedData = ByteBuffer.allocate((int) serializer.getLength(msg));
							serializer.serialize(msg, receivedData);
							receivedData.flip();
						} catch (Exception e) {
							e.printStackTrace();
						}
						List<ByteBuffer> ret = new ArrayList<>();
						ret.add(receivedData);
						// send TCPDataReceivedNotify event
						data.eventHandler
								.dataReceived(new TCPDataReceivedNotifyEvent(null, channel));
						return ret;
					}
				};
			}
		};
		// Create a LLRP client and open a channel
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		Logger origLog = Deencapsulation.getField(llrpClient, "log");
		Deencapsulation.setField(llrpClient, "log", Logger.getLogger("testing"));
		llrpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1, llrpEventHandler);
		// Await a LLRP message from LLRP server
		Message msg = llrpClient.awaitReceivedData(channel, 3000L);
		// A message which has been sent from the LLRP server is received.
		Assert.assertEquals(msg.getMessageHeader().getMessageType(),
				MessageType.SET_PROTOCOL_VERSION);

		new Verifications() {
			{
				// The LLRP client receives a LLRP event
				// LLRPDataReceivedNotifyEvent
				LLRPDataReceivedNotifyEvent event;
				llrpEventHandler.dataReceived(event = withCapture());
				times = 1;
				Assert.assertEquals(event.getChannel(), channel);
			}
		};

		Deencapsulation.setField(llrpClient, "log", origLog);
		channel.close();
	}

	/**
	 * Create a LLRP client but do not open a channel.
	 * <p>
	 * Await a LLRP message from a LLRP server.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>{@link LLRPUnknownChannelException}
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param channel
	 * @param llrpEventHandler
	 */
	public void awaitReceivedDataError1(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final SocketChannel channel) throws Exception {
		// Create a LLRP client but do not open a channel.
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		try {
			// Await a LLRP message from a LLRP server.
			llrpClient.awaitReceivedData(channel, 3000);
			Assert.fail();
		} catch (LLRPUnknownChannelException e) {
			Assert.assertTrue(e.getMessage().contains("Unknown channel"));
		}
	}

	/**
	 * Create a LLRP client and open a channel.
	 * <p>
	 * Await a LLRP message from a LLRP server. An invalid message header is
	 * sent to the LLRP client.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An exception is thrown.
	 * </ul>
	 * </p>
	 * Close the channel. The event contains received data which has not been
	 * sent to the LLRP client up to now.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>An {@link LLRPChannelClosedEvent} is sent with all the message data
	 * as pending received data.
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param channel
	 * @param llrpEventHandler
	 */
	public void awaitReceivedDataError2(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final SocketChannel channel, @Mocked final LLRPEventHandler llrpEventHandler)
			throws Exception {
		class TCPClientData {
			TCPEventHandler eventHandler;
		}
		final TCPClientData tcpClientData = new TCPClientData();
		final String host = InetAddress.getLocalHost().getHostName();
		new NonStrictExpectations() {
			{
				tcpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1,
						withInstanceOf(TCPEventHandler.class));
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestOpeningChannel(String host, int port,
							TCPEventHandler eventHandler)
							throws IOException, TCPUnknownChannelException {
						tcpClientData.eventHandler = eventHandler;
						eventHandler.channelOpened(new TCPChannelOpenedEvent(null, channel));
					}
				};

				tcpClient.awaitReceivedData(channel, anyLong);
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public List<ByteBuffer> awaitReceivedData(SocketChannel channel, long timeout)
							throws TCPUnknownChannelException, InterruptedException,
							TCPTimeoutException {
						// create invalid LLRP message (protocol version 0)
						List<ByteBuffer> ret = new ArrayList<>();
						for (int i = 0; i < 4; i++) {
							ByteBuffer data = ByteBuffer.allocate(4);
							data.put((byte) i);
							data.flip();
							ret.add(data);
						}
						return ret;
					}
				};

				tcpClient.requestClosingChannel(channel, false /* force */);
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestClosingChannel(SelectableChannel ch, boolean force) {
						ByteBuffer pendingReceivedData = ByteBuffer.allocate(1);
						pendingReceivedData.put((byte) 9);
						pendingReceivedData.flip();
						tcpClientData.eventHandler.channelClosed(new TCPChannelClosedEvent(null,
								channel, null, pendingReceivedData, null));
					}
				};
			}
		};
		// Create a LLRP client and open a channel
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		Logger origLog = Deencapsulation.getField(llrpClient, "log");
		Deencapsulation.setField(llrpClient, "log", Logger.getLogger("testing"));
		llrpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1, llrpEventHandler);
		try {
			// Await a LLRP message from a LLRP server. An invalid message
			// header is sent to the LLRP client.
			llrpClient.awaitReceivedData(channel, 10);
			Assert.fail();
		} catch (InvalidProtocolVersionException e) {
			// An exception is thrown.
			Assert.assertTrue(e.getMessage().contains("Invalid protocol version 0"));
		}
		llrpClient.requestClosingChannel(channel, false /* force */);

		new Verifications() {
			{
				// An LLRPChannelClosedEvent is sent with all the message data
				// as pending received data.
				LLRPChannelClosedEvent event;
				llrpEventHandler.channelClosed(event = withCapture());
				times = 1;

				ByteBuffer pendingReceivedData = event.getPendingReceivedData();
				ByteBuffer data = ByteBuffer.allocate(13);
				for (int i = 0; i < 3; i++) {
					data.putInt(0x00010203);
				}
				data.put((byte) 9);
				data.flip();
				Assert.assertEquals(pendingReceivedData, data);
			}
		};

		Deencapsulation.setField(llrpClient, "log", origLog);
	}

	/**
	 * Create a LLRP client and open a channel.
	 * <p>
	 * Await a LLRP message from a LLRP server. The TCP client throws a
	 * {@link TCPTimeoutException}.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>{@link LLRPTimeoutException}
	 * </ul>
	 * </p>
	 * <p>
	 * Await a LLRP message from a LLRP server. The TCP client does not sent all
	 * required data for a message in an expected period.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>{@link LLRPTimeoutException}
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param channel
	 * @param llrpEventHandler
	 */
	public void awaitReceivedDataError3(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final SocketChannel channel, @Mocked final LLRPEventHandler llrpEventHandler)
			throws Exception {
		final String host = InetAddress.getLocalHost().getHostName();
		new NonStrictExpectations() {
			{
				tcpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1,
						withInstanceOf(TCPEventHandler.class));
				result = new Delegate<TCPServerMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestOpeningChannel(String host, int port,
							TCPEventHandler eventHandler)
							throws IOException, TCPUnknownChannelException {
						eventHandler.channelOpened(new TCPChannelOpenedEvent(null, channel));
					}
				};

				// The TCP client throws a LLRPTimeoutException
				tcpClient.awaitReceivedData(channel, anyLong);
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public List<ByteBuffer> awaitReceivedData(SocketChannel channel, long timeout)
							throws TCPUnknownChannelException, InterruptedException,
							TCPTimeoutException {
						throw new TCPTimeoutException("huhu");
					}
				};
			}
		};
		// Create a LLRP client and open a channel
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		Logger origLog = Deencapsulation.getField(llrpClient, "log");
		Deencapsulation.setField(llrpClient, "log", Logger.getLogger("testing"));
		llrpClient.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1, llrpEventHandler);
		try {
			// Await a LLRP message from a LLRP server.
			llrpClient.awaitReceivedData(channel, 300);
			Assert.fail();
		} catch (LLRPTimeoutException e) {
			Assert.assertTrue(e.getMessage().contains("Time out"));
		}

		new NonStrictExpectations() {
			{
				// The TCP client does not sent all required data for a message
				// in an expected period.
				tcpClient.awaitReceivedData(channel, anyLong);
				result = new Delegate<TCPClientMultiplexed>() {
					@SuppressWarnings("unused")
					public List<ByteBuffer> awaitReceivedData(SocketChannel channel, long timeout)
							throws TCPUnknownChannelException, InterruptedException,
							TCPTimeoutException {
						Thread.sleep(100);
						List<ByteBuffer> ret = new ArrayList<>();
						ByteBuffer data = ByteBuffer.allocate(1);
						data.put((byte) 0);
						data.flip();
						ret.add(data);
						return ret;
					}
				};
			}
		};

		try {
			// Await a LLRP message from a LLRP server.
			llrpClient.awaitReceivedData(channel, 300);
			Assert.fail();
		} catch (LLRPTimeoutException e) {
			Assert.assertTrue(e.getMessage().contains("Time out"));
		}

		Deencapsulation.setField(llrpClient, "log", origLog);
	}

	/**
	 * Create an LLRP client and open a channel.
	 * <p>
	 * Close the channel.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The LLRP event handler receives a closing event.
	 * </ul>
	 * </p>
	 * 
	 * @param tcpClient
	 * @param channel
	 * @param llrpEventHandler
	 * @throws UnknownHostException
	 * @throws IOException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	public void requestClosingChannel(@Mocked final TCPClientMultiplexed tcpClient,
			@Mocked final SocketChannel channel, @Mocked final LLRPEventHandler llrpEventHandler)
			throws Exception {
		class TCPClientData {
			TCPEventHandler eventHandler;
		}
		final TCPClientData tcpClientData = new TCPClientData();
		new NonStrictExpectations() {
			{
				tcpClient.requestOpeningChannel(anyString, anyInt,
						withInstanceOf(TCPEventHandler.class));
				result = new Delegate<TCPServerMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestOpeningChannel(String host, int port,
							TCPEventHandler eventHandler)
							throws IOException, TCPUnknownChannelException {
						tcpClientData.eventHandler = eventHandler;
					}
				};

				tcpClient.requestClosingChannel(channel, false /* force */);
				result = new Delegate<TCPServerMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestClosingChannel(SelectableChannel ch, boolean force) {
						tcpClientData.eventHandler.channelClosed(
								new TCPChannelClosedEvent(null, channel, null, null, null));
					}
				};
			}
		};
		// Create an LLRP client and open a channel
		LLRPClientMultiplexed llrpClient = new LLRPClientMultiplexed(tcpClient);
		llrpClient.requestOpeningChannel(InetAddress.getLocalHost().getHostName(),
				_EnvTest.SERVER_PORT_1, llrpEventHandler);
		// Close the channel.
		llrpClient.requestClosingChannel(channel, false /* force */);
		new Verifications() {
			{
				// The LLRP event handler receives a closing event.
				LLRPChannelClosedEvent event;
				llrpEventHandler.channelClosed(event = withCapture());
				times = 1;
				Assert.assertEquals(event.getChannel(), channel);
			}
		};
	}

	/**
	 * Create a LLRP server and open a channel. Send and receive messages and
	 * close the channel.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The open/sent/close events are received.
	 * <li>Messages are received.
	 * </ul>
	 * </p>
	 * 
	 * @param tcpServer
	 * @param serverChannel
	 * @param channel
	 * @param llrpEventHandler
	 */
	public void server1(@Mocked final TCPServerMultiplexed tcpServer,
			@Mocked final ServerSocketChannel serverChannel, @Mocked final SocketChannel channel,
			@Mocked final LLRPEventHandler llrpEventHandler) throws Exception {
		class TCPServerData {
			TCPEventHandler eventHandler;
		}
		final TCPServerData tcpServerData = new TCPServerData();
		final String host = InetAddress.getLocalHost().getHostName();
		new NonStrictExpectations() {
			{
				tcpServer.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1,
						withInstanceOf(TCPEventHandler.class));
				result = new Delegate<TCPServerMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestOpeningChannel(String host, int port,
							TCPEventHandler eventHandler)
							throws IOException, TCPUnknownChannelException {
						tcpServerData.eventHandler = eventHandler;
						eventHandler
								.channelOpened(new TCPChannelOpenedEvent(serverChannel, channel));
					}
				};

				tcpServer.requestSendingData(withInstanceOf(SocketChannel.class),
						withInstanceOf(ByteBuffer.class));
				result = new Delegate<TCPServerMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestSendingData(SocketChannel channel, ByteBuffer data)
							throws TCPUnknownChannelException, TCPConnectorStoppedException {
						// "write" the data
						data.position(data.limit());
						// send a TCPDataSentEvent
						tcpServerData.eventHandler
								.dataSent(new TCPDataSentEvent(serverChannel, channel, data));
					}
				};

				tcpServer.awaitReceivedData(channel, anyLong);
				result = new Delegate<TCPServerMultiplexed>() {
					@SuppressWarnings("unused")
					public List<ByteBuffer> awaitReceivedData(SocketChannel channel, long timeout)
							throws TCPUnknownChannelException, InterruptedException,
							TCPTimeoutException {
						Message msg = new SetProtocolVersion(
								new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 1),
								ProtocolVersion.LLRP_V1_0_1);
						ByteBufferSerializer serializer = new ByteBufferSerializer();
						ByteBuffer data = null;
						try {
							data = ByteBuffer.allocate((int) serializer.getLength(msg));
							serializer.serialize(msg, data);
							data.flip();
						} catch (Exception e) {
							e.printStackTrace();
						}
						List<ByteBuffer> ret = new ArrayList<>();
						ret.add(data);
						return ret;
					}
				};

				tcpServer.requestClosingChannel(channel, false /* force */);
				result = new Delegate<TCPServerMultiplexed>() {
					@SuppressWarnings("unused")
					public void requestClosingChannel(SelectableChannel ch, boolean force) {
						tcpServerData.eventHandler.channelClosed(new TCPChannelClosedEvent(
								serverChannel, channel, null, null, null));
					}
				};
			}
		};
		// Create a LLRP server and open a channel
		LLRPServerMultiplexed llrpServer = new LLRPServerMultiplexed(tcpServer);
		Logger origLog = Deencapsulation.getField(llrpServer, "log");
		Deencapsulation.setField(llrpServer, "log", Logger.getLogger("testing"));
		llrpServer.requestOpeningChannel(host, _EnvTest.SERVER_PORT_1, llrpEventHandler);
		llrpServer.requestSendingData(channel,
				new SetProtocolVersion(new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1, 5),
						ProtocolVersion.LLRP_V1_0_1));
		// Messages are received.
		Message data = llrpServer.awaitReceivedData(channel, 10);
		Assert.assertEquals(data.getMessageHeader().getMessageType(),
				MessageType.SET_PROTOCOL_VERSION);
		llrpServer.requestClosingChannel(channel, false /* force */);

		new Verifications() {
			{
				// The open/sent/close events are received.
				LLRPChannelOpenedEvent openEvent;
				llrpEventHandler.channelOpened(openEvent = withCapture());
				times = 1;
				Assert.assertEquals(openEvent.getServerChannel(), serverChannel);
				Assert.assertEquals(openEvent.getChannel(), channel);

				LLRPDataSentEvent sentEvent;
				llrpEventHandler.dataSent(sentEvent = withCapture());
				times = 1;
				Assert.assertEquals(sentEvent.getServerChannel(), serverChannel);
				Assert.assertEquals(sentEvent.getChannel(), channel);
				Assert.assertEquals(sentEvent.getMessageId().longValue(), 5);

				LLRPChannelClosedEvent closeEvent;
				llrpEventHandler.channelClosed(closeEvent = withCapture());
				times = 1;
				Assert.assertEquals(closeEvent.getServerChannel(), serverChannel);
				Assert.assertEquals(closeEvent.getChannel(), channel);
			}
		};

		Deencapsulation.setField(llrpServer, "log", origLog);
	}
}
