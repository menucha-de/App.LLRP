package havis.llrpservice.server.llrp;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.common.tcp.TCPConnectorStoppedException;
import havis.llrpservice.csc.llrp.LLRPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPTimeoutException;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.ErrorMessage;
import havis.llrpservice.data.message.Keepalive;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.MessageTypes.MessageType;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.ReaderEventNotification;
import havis.llrpservice.data.message.parameter.ConnectionAttemptEvent;
import havis.llrpservice.data.message.parameter.ConnectionAttemptEventStatusType;
import havis.llrpservice.data.message.parameter.ConnectionCloseEvent;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.ReaderEventNotificationData;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;
import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

/**
 * The LLRPConnectionHandler handles the sending and receiving of data for an
 * established connection between a LLRP server and a client.
 * <p>
 * Data can be received with the blocking method {@link #awaitReceivedData()}
 * from a separate thread.
 * </p>
 * <p>
 * If the connection is closed by the client or a client connection is
 * interrupted because the server channel is closed then the blocking method
 * {@link #awaitReceivedData()} is released with an
 * {@link LLRPUnknownChannelException}.
 * </p>
 * <p>
 * An instance of LLRPConnectionHandler cannot be used for multiple connections.
 * For each connection a new instance must be created.
 * </p>
 */
class LLRPConnectionHandler {
	private final LLRPServerEventHandler serverEventHandler;
	private final SocketChannel clientChannel;
	private final Platform platform;
	private final List<LLRPMessageHandlerListener> listeners;
	private final LLRPMessageCreator messageCreator = new LLRPMessageCreator();
	private final Semaphore connectionConfirmationSent = new Semaphore(0);
	private final Object keepAliveHandlerLock = new Object();
	private LLRPKeepaliveHandler keepaliveHandler;
	private long keepAliveStopTimeout;

	private final Object protocolVersionLock = new Object();
	private ProtocolVersion protocolVersion = ProtocolVersion.LLRP_V1_0_1;

	/**
	 * @param serverEventHandler
	 * @param clientChannel
	 * @param platform
	 *            platform controller for getting UTC clock flag and platform
	 *            uptime
	 * @param listeners
	 *            listeners for
	 *            {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)}
	 *            events
	 */
	public LLRPConnectionHandler(LLRPServerEventHandler serverEventHandler,
			SocketChannel clientChannel, Platform platform,
			List<LLRPMessageHandlerListener> listeners) {
		this.serverEventHandler = serverEventHandler;
		this.clientChannel = clientChannel;
		this.platform = platform;
		this.listeners = new CopyOnWriteArrayList<LLRPMessageHandlerListener>(listeners);
	}

	public LLRPServerEventHandler getServerEventHandler() {
		return serverEventHandler;
	}

	/**
	 * Gets the channel of the connected client.
	 * 
	 * @return
	 */
	public SocketChannel getChannel() {
		return clientChannel;
	}

	/**
	 * Sets the protocol version which is used in notification messages with a
	 * {@link ConnectionCloseEvent} and {@link Keepalive} messages.
	 * 
	 * @param protocolVersion
	 */
	public void setProtocolVersion(ProtocolVersion protocolVersion) {
		synchronized (protocolVersionLock) {
			this.protocolVersion = protocolVersion;
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
	public void setKeepalive(long interval, long stopTimeout)
			throws InterruptedException, ExecutionException, TimeoutException {
		synchronized (keepAliveHandlerLock) {
			if (keepaliveHandler != null) {
				keepaliveHandler.stop(stopTimeout);
				keepaliveHandler = null;
			}
			if (interval > 0) {
				this.keepAliveStopTimeout = stopTimeout;
				keepaliveHandler = new LLRPKeepaliveHandler(this, interval);
				keepaliveHandler.start();
			}
		}
	}

	/**
	 * Adds a listener for
	 * {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)} events.
	 * 
	 * @param listener
	 */
	public void addListener(LLRPMessageHandlerListener listener) {
		listeners.add(listener);
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
	}

	/**
	 * Requests the sending of a LLRP message to the connected client.
	 * <p>
	 * If {@link #dataSent(LLRPDataSentEvent)} is called after the data has been
	 * sent then the event
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
		serverEventHandler.getLLRPServer().requestSendingData(clientChannel, message);
	}

	/**
	 * Creates a notification message with a {@link ConnectionAttemptEvent}.
	 * 
	 * @param status
	 * @return
	 * @throws PlatformException
	 */
	private ReaderEventNotification createReaderEventNotification(
			ConnectionAttemptEventStatusType status) throws PlatformException {
		ReaderEventNotificationData rend = messageCreator
				.createReaderEventNotificationData(platform);
		rend.setConnectionAttemptEvent(
				new ConnectionAttemptEvent(new TLVParameterHeader((byte) 0), status));
		return new ReaderEventNotification(new MessageHeader((byte) 0, ProtocolVersion.LLRP_V1_0_1,
				IdGenerator.getNextLongId()), rend);
	}

	/**
	 * Sends a notification message with a {@link ConnectionAttemptEvent} to the
	 * client. The status is set to
	 * {@link ConnectionAttemptEventStatusType#FAILED_CLIENT_CONNECTION_EXISTS}.
	 * 
	 * @throws InvalidMessageTypeException
	 * @throws LLRPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InvalidParameterTypeException
	 * @throws InterruptedException
	 * @throws PlatformException
	 */
	public void requestSendingConnectionDeniedEvent() throws InvalidMessageTypeException,
			LLRPUnknownChannelException, TCPConnectorStoppedException,
			InvalidParameterTypeException, InterruptedException, PlatformException {
		ReaderEventNotification message = createReaderEventNotification(
				ConnectionAttemptEventStatusType.FAILED_CLIENT_CONNECTION_EXISTS);
		requestSendingData(message);
		connectionConfirmationSent.release();
	}

	/**
	 * Sends a notification message with a {@link ConnectionAttemptEvent} to the
	 * client. The status is set to
	 * {@link ConnectionAttemptEventStatusType#SUCCESS}.
	 * 
	 * @throws InvalidMessageTypeException
	 * @throws LLRPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InvalidParameterTypeException
	 * @throws InterruptedException
	 * @throws PlatformException
	 * @throws TimeoutException
	 */
	public void requestSendingConnectionAcceptedEvent() throws InvalidMessageTypeException,
			LLRPUnknownChannelException, TCPConnectorStoppedException,
			InvalidParameterTypeException, InterruptedException, PlatformException {
		ReaderEventNotification message = createReaderEventNotification(
				ConnectionAttemptEventStatusType.SUCCESS);
		requestSendingData(message);
		connectionConfirmationSent.release();
	}

	/**
	 * Sends a notification message with a {@link ConnectionCloseEvent} to the
	 * client.
	 * <p>
	 * If {@link #dataSent(LLRPDataSentEvent)} is called after the data has been
	 * sent then the event
	 * {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)} is fired
	 * to all listeners.
	 * </p>
	 * 
	 * @throws InvalidMessageTypeException
	 * @throws LLRPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InvalidParameterTypeException
	 * @throws InterruptedException
	 * @throws PlatformException
	 * @throws TimeoutException
	 */
	public void requestSendingConnectionCloseEvent() throws InvalidMessageTypeException,
			LLRPUnknownChannelException, TCPConnectorStoppedException,
			InvalidParameterTypeException, InterruptedException, PlatformException {
		ReaderEventNotificationData rend = messageCreator
				.createReaderEventNotificationData(platform);
		rend.setConnectionCloseEvent(new ConnectionCloseEvent(new TLVParameterHeader((byte) 0)));
		ReaderEventNotification message;
		synchronized (protocolVersionLock) {
			message = new ReaderEventNotification(
					new MessageHeader((byte) 0, protocolVersion, IdGenerator.getNextLongId()),
					rend);
		}
		requestSendingData(message);
	}

	/**
	 * Sends a {@link Keepalive} message to the client.
	 * <p>
	 * If {@link #dataSent(LLRPDataSentEvent)} is called after the data has been
	 * sent then the event
	 * {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)} is fired
	 * to all listeners.
	 * </p>
	 * 
	 * @throws InvalidMessageTypeException
	 * @throws LLRPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InvalidParameterTypeException
	 * @throws InterruptedException
	 */
	public void requestSendingKeepaliveMessage()
			throws InvalidMessageTypeException, LLRPUnknownChannelException,
			TCPConnectorStoppedException, InvalidParameterTypeException, InterruptedException {
		Keepalive keepalive;
		synchronized (protocolVersionLock) {
			keepalive = new Keepalive(
					new MessageHeader((byte) 0, protocolVersion, IdGenerator.getNextLongId()));
		}
		requestSendingData(keepalive);
	}

	/**
	 * The event {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)}
	 * is fired to all listeners. Connection acceptance events are ignored.
	 * 
	 * @param event
	 */
	public void dataSent(LLRPDataSentEvent event) {
		// if it is not the event for a connection confirmation message
		if (!connectionConfirmationSent.tryAcquire()) {
			// fire event to all listeners
			for (LLRPMessageHandlerListener listener : listeners) {
				listener.dataSent(event);
			}
		}
	}

	/**
	 * Waits for a message from a LLRP client.
	 * <p>
	 * The waiting for a message can be canceled by closing the client channel
	 * via the LLRP server. A {@link LLRPUnknownChannelException} is thrown.
	 * </p>
	 * 
	 * @return received LLRP message
	 * 
	 * @throws LLRPUnknownChannelException
	 *             the client channel has been closed
	 * @throws InterruptedException
	 * @throws InvalidProtocolVersionException
	 * @throws InvalidMessageTypeException
	 * @throws InvalidParameterTypeException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws TCPConnectorStoppedException
	 */
	public Message awaitReceivedData() throws LLRPUnknownChannelException, InterruptedException,
			InvalidProtocolVersionException, InvalidMessageTypeException,
			InvalidParameterTypeException, ExecutionException, TimeoutException,
			TCPConnectorStoppedException {
		while (true) {
			LLRPStatusCode statusCode = LLRPStatusCode.M_SUCCESS;
			String errorMsg = null;
			try {
				Message msg = serverEventHandler.getLLRPServer().awaitReceivedData(clientChannel,
						LLRPServerMultiplexed.NO_TIMEOUT);
				// if a keepalive message has been received
				if (msg != null
						&& msg.getMessageHeader().getMessageType() == MessageType.KEEPALIVE_ACK) {
					synchronized (keepAliveHandlerLock) {
						if (keepaliveHandler != null) {
							// confirm the message
							keepaliveHandler.setAcknowledged(true);
						}
					}
				}
				return msg;
			} catch (LLRPTimeoutException e) {
				// no time out used => no time out exception
			} catch (InvalidProtocolVersionException e) {
				statusCode = LLRPStatusCode.M_UNSUPPORTED_VERSION;
				errorMsg = e.getMessage();
			} catch (InvalidMessageTypeException e) {
				statusCode = LLRPStatusCode.M_UNSUPPORTED_MESSAGE;
				errorMsg = e.getMessage();
			} catch (InvalidParameterTypeException e) {
				statusCode = LLRPStatusCode.M_PARAMETER_ERROR;
				errorMsg = e.getMessage();
			} catch (Exception e) {
				synchronized (keepAliveHandlerLock) {
					// stop a running keep alive handler
					if (keepaliveHandler != null) {
						keepaliveHandler.stop(keepAliveStopTimeout);
						keepaliveHandler = null;
					}
				}
				throw e;
			} finally {
				// if parsing of message failed
				if (LLRPStatusCode.M_SUCCESS != statusCode) {
					// send an error message
					ErrorMessage msg;
					synchronized (protocolVersionLock) {
						msg = new ErrorMessage(
								new MessageHeader((byte) 0, protocolVersion,
										IdGenerator.getNextLongId()),
								new LLRPStatus(new TLVParameterHeader((byte) 0), statusCode,
										errorMsg));
					}
					requestSendingData(msg);
				}
			}
		}
	}

}
