package havis.llrpservice.csc.llrp;

import havis.llrpservice.common.tcp.TCPClientMultiplexed;
import havis.llrpservice.common.tcp.TCPConnectorStoppedException;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.common.tcp.TCPTimeoutException;
import havis.llrpservice.common.tcp.TCPUnknownChannelException;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * A LLRP client implementation. It allows the opening of multiple channels to
 * several servers.
 * <p>
 * The LLRP client uses a TCP client for connections to the servers. The TCP
 * client must be started before.
 * <p>
 * While a channel is being opened with
 * {@link #requestOpeningChannel(String, int, LLRPEventHandler)} it is assigned
 * to an event handler which must implement the interface
 * {@link LLRPEventHandler}. The client fires events to the event handler after
 * the relating channel has been opened, data has been sent or the channel has
 * been closed.
 * </p>
 * <p>
 * After the channel has been opened data can be sent to the server with
 * {@link #requestSendingData(SocketChannel, Message)} .
 * </p>
 * <p>
 * A single channel can be stopped with
 * {@link #requestClosingChannel(SelectableChannel)}.
 * </p>
 */
public class LLRPClientMultiplexed extends AbstractLLRPConnectorMultiplexed {
	private final TCPClientMultiplexed client;

	public LLRPClientMultiplexed(TCPClientMultiplexed client) throws IOException {
		this.client = client;
	}

	@Override
	void tcpRequestOpeningChannel(String host, int port, TCP2LLRPEventHandler eventHandler)
			throws IOException, TCPConnectorStoppedException {
		client.requestOpeningChannel(host, port, eventHandler);
	}

	@Override
	void tcpRequestSendingData(SocketChannel channel, ByteBuffer data)
			throws TCPUnknownChannelException, TCPConnectorStoppedException {
		client.requestSendingData(channel, data);
	}

	@Override
	List<ByteBuffer> tcpAwaitReceivedData(SocketChannel channel, long timeout)
			throws TCPUnknownChannelException, InterruptedException, TCPTimeoutException {
		return client.awaitReceivedData(channel, timeout);
	}

	// make method visible for JMockit
	@Override
	public Message awaitReceivedData(SocketChannel channel, long timeout)
			throws LLRPUnknownChannelException, LLRPTimeoutException, InterruptedException,
			InvalidProtocolVersionException, InvalidMessageTypeException,
			InvalidParameterTypeException {
		return super.awaitReceivedData(channel, timeout);
	}

	/**
	 * See {@link TCPServerMultiplexed#requestClosingChannel(SelectableChannel)}
	 * 
	 * @throws LLRPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	public void requestClosingChannel(SelectableChannel channel, boolean force)
			throws LLRPUnknownChannelException, TCPConnectorStoppedException {
		try {
			client.requestClosingChannel(channel, force);
		} catch (TCPUnknownChannelException e) {
			throw new LLRPUnknownChannelException(e);
		}
	}
}
