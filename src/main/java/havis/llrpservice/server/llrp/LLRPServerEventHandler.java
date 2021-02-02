package havis.llrpservice.server.llrp;

import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.llrpservice.common.concurrent.EventPipe;
import havis.llrpservice.common.concurrent.EventPipes;
import havis.llrpservice.common.tcp.TCPConnectorStoppedException;
import havis.llrpservice.csc.llrp.LLRPEventHandler;
import havis.llrpservice.csc.llrp.LLRPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataReceivedNotifyEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.Keepalive;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageTypes.MessageType;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.ConnectionCloseEvent;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;
import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

/**
 * The LLRPServerEventHandler handles the events for a LLRP server. It opens and
 * closes a server channel for clients. Only one client is accepted. Further
 * client connections are rejected.
 * <p>
 * Data can be received with the blocking method {@link #awaitReceivedData()}
 * from a separate thread.
 * </p>
 * <p>
 * If a client connection is closed by the client or a client connection is
 * interrupted because the server channel is closed then the blocking method
 * {@link #awaitReceivedData()} is released with a
 * {@link LLRPUnknownChannelException}.
 * </p>
 * <p>
 * The execution of the event handler can be canceled with
 * {@link #cancelExecution()}. After that the instance of LLRPServerEventHandler
 * can no longer be used for further connections.
 * </p>
 */
class LLRPServerEventHandler implements LLRPEventHandler {

	private static final Logger log = Logger.getLogger(LLRPServerEventHandler.class.getName());

	private final LLRPServerMultiplexed llrpServer;
	private final Platform platformController;
	private List<LLRPMessageHandlerListener> listeners;
	private ProtocolVersion protocolVersion = ProtocolVersion.LLRP_V1_0_1;
	private long keepAliveInterval = 0;
	private long keepAliveStopTimeout = 0;

	// created after the open event of the channel has been received
	private LLRPConnectionHandler connectionHandler;
	// is set after the open event of the channel has been received
	// is unset after the acceptance message for the connection has been sent
	private LLRPChannelOpenedEvent openEvent;
	// is set when the response for a ConnectionClose message is being sent
	private boolean isCloseConnectionResponseMessageSent = false;
	private boolean isAborted = false;
	private Exception abortException = null;

	private ReentrantLock lock = new ReentrantLock();
	private Integer AWAIT_RECEIVED_DATA_EVENT = 0;
	// latches for LLRPChannelOpenedEvent, LLRPChannelClosedEvent, Integer
	private EventPipes latches = new EventPipes(lock);

	/**
	 * @param llrpServer
	 * @param platformController
	 *            platform controller for getting UTC clock flag and platform
	 *            uptime
	 * @param listeners
	 *            listeners for
	 *            {@link LLRPMessageHandlerListener#opened(LLRPChannelOpenedEvent)}
	 *            ,
	 *            {@link LLRPMessageHandlerListener#closed(LLRPChannelClosedEvent)}
	 *            and
	 *            {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)}
	 *            events
	 */
	public LLRPServerEventHandler(LLRPServerMultiplexed llrpServer, Platform platformController,
			List<LLRPMessageHandlerListener> listeners) {
		this.llrpServer = llrpServer;
		this.platformController = platformController;
		this.listeners = new CopyOnWriteArrayList<LLRPMessageHandlerListener>(listeners);
	}

	public LLRPServerMultiplexed getLLRPServer() {
		return llrpServer;
	}

	public String getConnectionLocalHostAddress() {
		lock.lock();
		try {
			if (connectionHandler != null) {
				return connectionHandler.getChannel().socket().getLocalAddress().getHostAddress();
			}
			return null;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Sets the protocol version which is used in notification messages with a
	 * {@link ConnectionCloseEvent} and {@link Keepalive} messages.
	 * 
	 * @param protocolVersion
	 */
	public void setProtocolVersion(ProtocolVersion protocolVersion) {
		lock.lock();
		try {
			this.protocolVersion = protocolVersion;
			if (connectionHandler != null) {
				connectionHandler.setProtocolVersion(protocolVersion);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Enables/Disables the sending of {@link Keepalive} messages.
	 * 
	 * @param interval
	 *            the keep alive interval in milliseconds; an interval &lt;= 0
	 *            disables the keep alive
	 * @param stopTimeout
	 *            the time out in milliseconds for stopping the keep alive
	 *            thread
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public void setKeepaliveInterval(long interval, long stopTimeout)
			throws InterruptedException, ExecutionException, TimeoutException {
		lock.lock();
		try {
			keepAliveInterval = interval;
			keepAliveStopTimeout = stopTimeout;
			if (connectionHandler != null) {
				connectionHandler.setKeepalive(interval, stopTimeout);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Adds a listener for the client events
	 * {@link LLRPMessageHandlerListener#opened(LLRPChannelOpenedEvent)},
	 * {@link LLRPMessageHandlerListener#closed(LLRPChannelClosedEvent)} and
	 * {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)}.
	 * 
	 * @param listener
	 */
	public void addListener(LLRPMessageHandlerListener listener) {
		listeners.add(listener);
		lock.lock();
		try {
			if (connectionHandler != null) {
				connectionHandler.addListener(listener);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(LLRPMessageHandlerListener listener) {
		List<LLRPMessageHandlerListener> removed = new ArrayList<LLRPMessageHandlerListener>();
		for (LLRPMessageHandlerListener entry : listeners) {
			if (listener == entry) {
				removed.add(entry);
			}
		}
		listeners.removeAll(removed);
		lock.lock();
		try {
			if (connectionHandler != null) {
				connectionHandler.removeListener(listener);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Cancels the execution of the event handler. The closing of an opened
	 * client channel is requested and a thread which is blocked in
	 * {@link #awaitReceivedData()} is released without an exception.
	 * <p>
	 * The instance of LLRPServerEventHandler cannot be used for further
	 * connections.
	 * </p>
	 * 
	 * @throws InterruptedException
	 * @throws InvalidParameterTypeException
	 * @throws InvalidMessageTypeException
	 * @throws PlatformException
	 */
	public void cancelExecution() throws InvalidMessageTypeException, InvalidParameterTypeException,
			InterruptedException, PlatformException {
		abortExecution(null);
	}

	/**
	 * Aborts the execution of the event handler. The closing of an opened
	 * client channel is requested and a thread which is blocked in
	 * {@link #awaitReceivedData()} is released with the given abort exception.
	 * <p>
	 * The instance of LLRPServerEventHandler cannot be used for further
	 * connections.
	 * </p>
	 * 
	 * @throws InterruptedException
	 * @throws InvalidParameterTypeException
	 * @throws InvalidMessageTypeException
	 * @throws PlatformException
	 */
	public void abortExecution(Exception abortException) throws InvalidMessageTypeException,
			InvalidParameterTypeException, InterruptedException, PlatformException {
		lock.lock();
		try {
			isAborted = true;
			this.abortException = abortException;
			// release the waiting for a LLRP connection in "awaitDataReceived"
			latches.fire(AWAIT_RECEIVED_DATA_EVENT);
			// if a connection handler is active
			if (connectionHandler != null) {
				try {
					// if the closing is triggered by the LLRP server
					if (!isCloseConnectionResponseMessageSent) {
						// send a connection close event
						connectionHandler.requestSendingConnectionCloseEvent();
					}
					// close the client connection
					llrpServer.requestClosingChannel(connectionHandler.getChannel(),
							abortException != null /* force */);
				} catch (LLRPUnknownChannelException | TCPConnectorStoppedException e) {
					// the channel has already been closed
				}
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Requests the sending of a LLRP message to the connected client.
	 * <p>
	 * If the {@link #dataSent(LLRPDataSentEvent)} event is received from the
	 * LLRP server after the data has been sent then the event
	 * {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)} is fired
	 * to all listeners.
	 * </p>
	 * 
	 * @param message
	 * @throws InvalidMessageTypeException
	 * @throws LLRPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InvalidParameterTypeException
	 * @throws InterruptedException
	 */
	public void requestSendingData(Message message)
			throws InvalidMessageTypeException, LLRPUnknownChannelException,
			TCPConnectorStoppedException, InvalidParameterTypeException, InterruptedException {
		lock.lock();
		try {
			if (connectionHandler != null) {
				if (message.getMessageHeader()
						.getMessageType() == MessageType.CLOSE_CONNECTION_RESPONSE) {
					isCloseConnectionResponseMessageSent = true;
				}
				connectionHandler.requestSendingData(message);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Awaits the opening of the server channel.
	 * 
	 * @param timeout
	 *            time out in milliseconds
	 * @return
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public ServerSocketChannel awaitServerOpening(long timeout)
			throws InterruptedException, TimeoutException {
		List<LLRPChannelOpenedEvent> events = latches.await(LLRPChannelOpenedEvent.class, timeout);
		return events.get(0).getServerChannel();
	}

	/**
	 * Awaits the closing of the server channel.
	 * 
	 * @param timeout
	 *            time out in milliseconds
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public void awaitServerClosing(long timeout) throws InterruptedException, TimeoutException {
		latches.await(LLRPChannelClosedEvent.class, timeout);
	}

	/**
	 * If a server channel is opened then {@link #awaitServerOpening(long)} is
	 * released.
	 * <p>
	 * If a client channel is opened and a client connection already exists then
	 * the connection attempt is denied by sending a deny message and closing
	 * the channel. If no client connection exists then the client channel can
	 * be accepted and an acceptance message is sent.
	 * </p>
	 * 
	 * @param event
	 */
	@Override
	public void channelOpened(LLRPChannelOpenedEvent event) {
		lock.lock();
		try {
			// avoid opening a new channel if the execution of the event handler
			// has been aborted
			if (isAborted) {
				return;
			}
			// if client channel
			if (event.getChannel() != null) {
				// if a connection is already handled
				if (connectionHandler != null) {
					// send a deny message via a temporary connection
					// handler (the "sent" event is ignored in "dataSent")
					LLRPConnectionHandler deniedHandler = new LLRPConnectionHandler(this,
							event.getChannel(), platformController,
							new ArrayList<LLRPMessageHandlerListener>());
					deniedHandler.requestSendingConnectionDeniedEvent();
					// close the client channel (the "close" event is
					// ignored in "channelClosed" method)
					llrpServer.requestClosingChannel(event.getChannel(), false /* force */);
				} else {
					// create a connection handler and send an acceptance
					// message
					connectionHandler = new LLRPConnectionHandler(this, event.getChannel(),
							platformController, listeners);
					connectionHandler.setProtocolVersion(protocolVersion);
					connectionHandler.setKeepalive(keepAliveInterval, keepAliveStopTimeout);
					connectionHandler.requestSendingConnectionAcceptedEvent();
					openEvent = event;
					isCloseConnectionResponseMessageSent = false;
				}
			} else {
				latches.fire(event);
			}
		} catch (Throwable t) {
			log.log(Level.SEVERE, "Opening of channel failed", t);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * If a server channel is closed then {@link #awaitServerClosing(long)} is
	 * released.
	 * <p>
	 * If an accepted client channel is closed then
	 * {@link LLRPMessageHandlerListener#closed(LLRPChannelClosedEvent)} is
	 * fired to all listeners.
	 * </p>
	 * <p>
	 * Events for denied connections are ignored.
	 * </p>
	 * 
	 * @param event
	 */
	@Override
	public void channelClosed(LLRPChannelClosedEvent event) {
		boolean isClosed = false;
		lock.lock();
		try {
			// if client channel
			if (event.getChannel() != null) {
				// if accepted connection (denied connections are ignored)
				if (connectionHandler != null
						&& event.getChannel() == connectionHandler.getChannel()) {
					// delete connection handler
					connectionHandler = null;
					isClosed = true;
				}
			} else {
				latches.fire(event);
			}
		} finally {
			lock.unlock();
		}
		if (isClosed) {
			// forward close event to all listeners
			for (LLRPMessageHandlerListener listener : listeners) {
				listener.closed(event);
			}
		}
	}

	/**
	 * If the sent message was a connection acceptance event then
	 * {@link LLRPMessageHandlerListener#opened(LLRPChannelOpenedEvent)} is
	 * fired to all listeners else
	 * {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)}.
	 * <p>
	 * Events for denied connections are ignored.
	 * </p>
	 * 
	 * @param event
	 */
	@Override
	public void dataSent(LLRPDataSentEvent event) {
		LLRPChannelOpenedEvent openedEventLocal = null;
		lock.lock();
		try {
			// if accepted connection (denied connections are ignored)
			if (connectionHandler != null && event.getChannel() == connectionHandler.getChannel()) {
				// forward the event to the connection handler
				connectionHandler.dataSent(event);
				// if the acceptance message has been sent
				if (openEvent != null) {
					// start the receiving of data in "awaitDataReceived"
					latches.fire(AWAIT_RECEIVED_DATA_EVENT);
					openedEventLocal = openEvent;
					openEvent = null;
				}
			}
		} finally {
			lock.unlock();
		}
		if (openedEventLocal != null) {
			// send open events
			for (LLRPMessageHandlerListener listener : listeners) {
				listener.opened(openedEventLocal);
			}
		}
	}

	/**
	 * This event is ignored.
	 */
	@Override
	public void dataReceived(LLRPDataReceivedNotifyEvent event) {
	}

	/**
	 * Waits for a message from a LLRP client. If no client is connected then
	 * for a new client is waited.
	 * <p>
	 * If the execution of the event handler is canceled with
	 * {@link #cancelExecution()} then <code>null</code> is returned.
	 * </p>
	 * <p>
	 * If a client connection is closed by the client or a client connection is
	 * interrupted because the server channel is closed then a
	 * {@link LLRPUnknownChannelException} is thrown.
	 * </p>
	 * 
	 * @return LLRP message or <code>null</code> if the waiting for a client was
	 *         canceled
	 * @throws TimeoutException
	 * @throws ExecutionException
	 * @throws InvalidParameterTypeException
	 * @throws InvalidMessageTypeException
	 * @throws InvalidProtocolVersionException
	 * @throws LLRPUnknownChannelException
	 *             the client channel has been closed
	 * @throws InterruptedException
	 * @throws TCPConnectorStoppedException
	 * 
	 * 
	 * @throws Throwable
	 */
	public Message awaitReceivedData() throws InterruptedException, LLRPUnknownChannelException,
			InvalidProtocolVersionException, InvalidMessageTypeException,
			InvalidParameterTypeException, ExecutionException, TimeoutException,
			TCPConnectorStoppedException {
		LLRPConnectionHandler connectionHandlerLocal;
		lock.lock();
		try {
			if (isAborted) {
				if (abortException != null) {
					throw new LLRPUnknownChannelException(abortException);
				}
				return null;
			}
			// while no client connection has been opened or the acceptance
			// message has not been sent yet
			// (loop: an open event can be received after the connection has
			// been closed)
			while (connectionHandler == null || openEvent != null) {
				if (log.isLoggable(Level.INFO)) {
					log.log(Level.INFO, "Waiting for a LLRP connection");
				}
				// wait for AWAIT_RECEIVED_DATA_EVENT
				latches.await(Integer.class, EventPipe.NO_TIMEOUT);
				if (isAborted) {
					if (log.isLoggable(Level.INFO)) {
						log.log(Level.INFO, "Aborted the waiting for a LLRP connection");
					}
					if (abortException != null) {
						throw new LLRPUnknownChannelException(abortException);
					}
					return null;
				}
			}
			connectionHandlerLocal = connectionHandler;
		} finally {
			lock.unlock();
		}
		try {
			// wait for LLRP message
			return connectionHandlerLocal.awaitReceivedData();
		} catch (LLRPUnknownChannelException e) {
			// the CANCELED flag was set before the client channel has
			// been closed
			if (isAborted) {
				if (log.isLoggable(Level.INFO)) {
					log.log(Level.INFO, "Aborted the waiting for LLRP messages");
				}
				if (abortException != null) {
					throw new LLRPUnknownChannelException(abortException);
				}
				return null;
			}
			// the remote side has closed the connection or the server channel
			// has been closed => throw the exception
			throw e;
		}
	}

}
