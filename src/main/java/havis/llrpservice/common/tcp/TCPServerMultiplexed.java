package havis.llrpservice.common.tcp;

import havis.llrpservice.common.tcp.event.TCPChannelClosedEvent;
import havis.llrpservice.common.tcp.event.TCPChannelOpenedEvent;
import havis.llrpservice.common.tcp.event.TCPDataReceivedNotifyEvent;
import havis.llrpservice.common.tcp.event.TCPDataSentEvent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A TCP server implementation. It allows the opening of multiple channels.
 * <p>
 * The server implements the {@link Runnable} interface, thereby it can be
 * started in a separate thread or directly with method {@link #run()}.
 * </p>
 * <p>
 * While a channel is being opened with
 * {@link #requestOpeningChannel(String, int, TCPEventHandler)} it is assigned
 * to an event handler which must implement the interface
 * {@link TCPEventHandler}. The server fires events to the event handler after
 * the relating channel has been opened, data has been sent/received or the
 * channel has been closed.
 * </p>
 * <p>
 * After the channel has been opened data can be sent to a client with
 * {@link #requestSendingData(SocketChannel, java.nio.ByteBuffer)}.
 * </p>
 * <p>
 * A single channel can be stopped with
 * {@link #requestClosingChannel(SelectableChannel)}. The server sends a closing
 * event to the event handler.
 * </p>
 * <p>
 * The server can be stopped with {@link #requestClosing()}. All open channels
 * are closed, closing events are sent to the event handlers and the method
 * {@link #run()} finishes.
 * </p>
 */
public class TCPServerMultiplexed extends AbstractTCPConnectorMultiplexed {
	/*
	 * Thread safe: The events are fired from another thread (selector thread).
	 */
	private Map<SelectableChannel, TCPEventHandler> eventHandlers = new ConcurrentHashMap<SelectableChannel, TCPEventHandler>();

	public TCPServerMultiplexed() throws IOException {
		super();
	}

	/**
	 * Requests the opening of a channel to an address/port. The method
	 * {@link TCPEventHandler#channelOpened(TCPChannelOpenedEvent)} of the event
	 * handler is called after the channel has been opened.
	 * 
	 * @param host
	 * @param port
	 * @param eventHandler
	 * @throws IOException
	 * @throws TCPUnknownChannelException
	 *             a new channel is not accepted because the server is stopped
	 * @throws TCPConnectorStoppedException
	 */
	public void requestOpeningChannel(String host, int port, TCPEventHandler eventHandler)
			throws IOException, TCPConnectorStoppedException {
		// create a new non-blocking server socket channel (a selector is used
		// for blocking)
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		// bind the server socket to the specified address and port
		serverChannel.bind(new InetSocketAddress(host, port));
		// register the event handler
		eventHandlers.put(serverChannel, eventHandler);
		// enqueue a channel registration (the caller is not the
		// selecting thread)
		try {
			enqueueChangeRequest(new ChangeRequest(serverChannel, true /* isServerSocketChannel */,
					ChangeRequest.ChangeType.REGISTER, SelectionKey.OP_ACCEPT, false /* force */,
					null /* sendingData */));
		} catch (TCPUnknownChannelException e) {
			// a new channel is always unknown
		} catch (Throwable t) {
			eventHandlers.remove(serverChannel);
			serverChannel.close();
			throw t;
		}
	}

	// make method visible for JMockit
	@Override
	public List<ByteBuffer> awaitReceivedData(SocketChannel channel, long timeout)
			throws TCPUnknownChannelException, InterruptedException, TCPTimeoutException {
		return super.awaitReceivedData(channel, timeout);
	}

	// make method visible for JMockit
	@Override
	public void requestSendingData(SocketChannel channel, ByteBuffer data)
			throws TCPUnknownChannelException, TCPConnectorStoppedException {
		super.requestSendingData(channel, data);
	}

	// make method visible for JMockit
	@Override
	public void requestClosingChannel(SelectableChannel channel, boolean force)
			throws TCPUnknownChannelException, TCPConnectorStoppedException {
		super.requestClosingChannel(channel, force);
	}

	@Override
	void channelOpened(TCPChannelOpenedEvent event) {
		// delegate event to event handler
		eventHandlers.get(event.getServerChannel()).channelOpened(event);
	}

	@Override
	void dataSent(TCPDataSentEvent event) {
		// delegate event to event handler
		eventHandlers.get(event.getServerChannel()).dataSent(event);
	}

	@Override
	void dataReceived(TCPDataReceivedNotifyEvent event) {
		// delegate event to event handler
		eventHandlers.get(event.getServerChannel()).dataReceived(event);
	}

	@Override
	void channelClosed(TCPChannelClosedEvent event) {
		ServerSocketChannel serverChannel = event.getServerChannel();
		TCPEventHandler eventHandler = null;
		// if a server channel has been closed
		if (event.getChannel() == null) {
			// unregister the server channel and get the event handler
			eventHandler = eventHandlers.remove(serverChannel);
		} else {
			// get the event handler using the relating server channel
			eventHandler = eventHandlers.get(serverChannel);
		}
    // if channel has not been closed up to now
    if (eventHandler != null) {
  		// delegate event to event handler
  		eventHandler.channelClosed(event);
    }
	}
}
