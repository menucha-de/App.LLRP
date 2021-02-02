package havis.llrpservice.sbc.gpio;

import havis.device.io.Configuration;
import havis.device.io.IOConsumer;
import havis.device.io.IODevice;
import havis.device.io.StateEvent;
import havis.device.io.Type;
import havis.device.io.exception.ConnectionException;
import havis.device.io.exception.ImplementationException;
import havis.device.io.exception.ParameterException;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.sbc.gpio.event.GPIOChannelClosedEvent;
import havis.llrpservice.sbc.gpio.event.GPIOChannelOpenedEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataReceivedNotifyEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataSentEvent;
import havis.llrpservice.sbc.gpio.json.GPIOJacksonMixIns;
import havis.llrpservice.sbc.gpio.message.ConnectionAttempted;
import havis.llrpservice.sbc.gpio.message.GetConfiguration;
import havis.llrpservice.sbc.gpio.message.GetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.KeepAlive;
import havis.llrpservice.sbc.gpio.message.Message;
import havis.llrpservice.sbc.gpio.message.MessageHeader;
import havis.llrpservice.sbc.gpio.message.MessageType;
import havis.llrpservice.sbc.gpio.message.ResetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.SetConfiguration;
import havis.llrpservice.sbc.gpio.message.SetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.StateChanged;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactoryException;

import java.io.IOException;
import java.lang.Thread.State;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A GPIO server implementation. It allows the opening of multiple channels to
 * several servers.
 * <p>
 * While a channel is being opened with
 * {@link #requestOpeningChannel(String, int, GPIOEventHandler)} it is assigned
 * to an event handler which must implement the interface
 * {@link GPIOEventHandler}. The server fires events to the event handler after
 * the relating channel has been opened, data has been sent or the channel has
 * been closed.
 * </p>
 * <p>
 * After the channel has been opened data can be sent to the client with
 * {@link #requestSendingData(SocketChannel, Message)}.
 * </p>
 * <p>
 * A single channel can be stopped with
 * {@link #requestClosingChannel(SelectableChannel)}.
 * </p>
 */
public class GPIOClientMultiplexed {

	private final static Logger log = Logger.getLogger(GPIOClientMultiplexed.class.getName());

	public static final int NO_TIMEOUT = -1;
	public static final int RETURN_IMMEDIATELY = 0;

	private final ServiceFactory<IODevice> serviceFactory;
	private long openTimeout;

	private final ReentrantLock lock = new ReentrantLock();
	private final Map<SocketChannel, ChannelData> channels = new HashMap<>();

	private class ChannelData {
		IODevice controller;
		GPIOEventHandler eventHandler;
		List<Message> messageQueue = new ArrayList<>();
		Condition messageQueueContainsMessage;
		List<Message> sendingMessages = new ArrayList<>();

		public void enqueueMessage(Message message) {
			messageQueue.add(message);
			messageQueueContainsMessage.signal();
		}
	}

	private class GPIOConsumerImpl implements IOConsumer {

		private final SocketChannel channel;

		public GPIOConsumerImpl(SocketChannel channel) {
			this.channel = channel;
		}

		@Override
		public void connectionAttempted() {
			enqueueMessage(new ConnectionAttempted(new MessageHeader(
					IdGenerator.getNextLongId())));
		}

		@Override
		public void stateChanged(StateEvent e) {
			enqueueMessage(new StateChanged(new MessageHeader(
					IdGenerator.getNextLongId()), e));
		}

		@Override
		public void keepAlive() {
			enqueueMessage(new KeepAlive(new MessageHeader(
					IdGenerator.getNextLongId())));
		}

		/**
		 * Enqueues a received message to the local list and triggers the
		 * delivering of the message.
		 * 
		 * @param message
		 */
		private void enqueueMessage(Message message) {
			logReceivedMessage(message);
			lock.lock();
			try {
				ChannelData channelData = channels.get(channel);
				if (channelData != null) {
					channelData.enqueueMessage(message);
				}
			} finally {
				lock.unlock();
			}
		}
	}

	/**
	 * @param openTimeout
	 *            time out for {@link IODevice#openConnection(IOConsumer, int)}
	 *            call in ms
	 * @param serviceFactory
	 */
	public GPIOClientMultiplexed(ServiceFactory<IODevice> serviceFactory,
			int openTimeout) {
		this.serviceFactory = serviceFactory;
		this.openTimeout = openTimeout;
	}

	/**
	 * Requests the opening of a channel to an address/port. The method
	 * {@link GPIOEventHandler#channelOpened(GPIOChannelOpenedEvent)} of the
	 * event handler is called after the channel has been opened.
	 * 
	 * @param addr
	 * @param port
	 * @param eventHandler
	 */
	public void requestOpeningChannel(String addr, int port,
			GPIOEventHandler eventHandler) throws GPIOException {
		long start = System.currentTimeMillis();
		ChannelData channelData = new ChannelData();
		SocketChannel channel = null;
		lock.lock();
		try {

			try {
				// instantiate the GPIO controller
				channelData.controller = serviceFactory.getService(addr, port,
						openTimeout);
				channelData.eventHandler = eventHandler;
				channelData.messageQueueContainsMessage = lock.newCondition();
				// create a channel
				channel = SocketChannel.open();
				channels.put(channel, channelData);
				// open a connection to the GPIO controller
				long remainingTimeout = openTimeout
						- (System.currentTimeMillis() - start);
				lock.unlock();
				try {
					// while the execution is running neither a
					// further method of the controller must be
					// called nor the connection must be closed!
					channelData.controller.openConnection(new GPIOConsumerImpl(
							channel), (int) (remainingTimeout <= 0 ? 1
							: remainingTimeout));
				} finally {
					lock.lock();
				}
			} catch (Exception e) {
				if (channel != null) {
					channels.remove(channel);
					try {
						channel.close();
					} catch (IOException e2) {
						// only log this exception because the first occurred
						// exception is thrown
						log.log(Level.SEVERE, "Cannot close socket channel", e2);
					}
				}
				throw new GPIOConnectionException(
						"Cannot create a connection to the GPIO controller", e);
			}
		} finally {
			lock.unlock();
		}
		// send open event
		eventHandler.channelOpened(new GPIOChannelOpenedEvent(
				null/* serverChannel */, channel));
	}

	/**
	 * Sends a message to a channel.
	 * <p>
	 * The channel must be opened before with
	 * {@link #requestOpeningChannel(String, int, GPIOEventHandler)}.
	 * </p>
	 * 
	 * @param channel
	 * @param message
	 */
	public void requestSendingData(SocketChannel channel, Message message) {
		ChannelData channelData = null;
		GPIOEventHandler eventHandler = null;
		lock.lock();
		try {
			channelData = channels.get(channel);
			// save event handler locally to sent events outside the
			// synchronized block
			eventHandler = channelData.eventHandler;
			switch (message.getMessageHeader().getMessageType()) {
			case GET_CONFIGURATION:
			case SET_CONFIGURATION:
			case RESET_CONFIGURATION:
				// add message to the channel data
				// The message is being processed synchronously when its
				// response is expected in awaitReceivedData.
				channelData.enqueueMessage(message);
				break;
			}
		} finally {
			lock.unlock();
		}
		// fire "dataSent" event
		// no serialization necessary => no serialization exception and no
		// pending data
		eventHandler.dataSent(new GPIODataSentEvent(null/* serverChannel */,
				channel, message.getMessageHeader().getId(), /* pendingData */
				null, /* exception */null));
	}

	/**
	 * Dequeues received data for a channel. If the queue is empty the calling
	 * thread changes to {@link State#WAITING} until new data are available or
	 * the specified waiting time elapses.
	 * 
	 * @param channel
	 * @param timeout
	 *            <ul>
	 *            <li>&lt; 0: wait until data are received (see
	 *            {@link #NO_TIMEOUT})
	 *            <li>0: return existing data immediately (see
	 *            {@link #RETURN_IMMEDIATELY})
	 *            <li>&gt; 0: wait until data are received or the specified
	 *            waiting time elapses (in milliseconds)
	 *            </ul>
	 * @return GPIO message or <code>null</code> if {@link #NO_TIMEOUT} is used
	 *         and no message has been received
	 * @throws InterruptedException
	 * @throws GPIOException
	 */
	public Message awaitReceivedData(SocketChannel channel, long timeout)
			throws InterruptedException, GPIOException {
		Message message = null;
		GPIOEventHandler eventHandler = null;
		GPIOException exception = null;
		List<Message> pendingSendingData = null;
		List<Message> pendingReceivedData = null;
		lock.lock();
		try {
			ChannelData channelData = channels.get(channel);
			while (channelData.messageQueue.size() == 0 && timeout != 0) {
				if (timeout < 0) {
					channelData.messageQueueContainsMessage.await();
				} else if (!channelData.messageQueueContainsMessage.await(
						timeout, TimeUnit.MILLISECONDS)) {
					throw new GPIOTimeoutException("Time out after " + timeout
							+ " ms while waiting for received messages");
				}
				// if the channel has been closed while waiting for data
				if (!channels.containsKey(channel)) {
					throw new GPIOUnknownChannelException("Closed channel: "
							+ channel);
				}
			}
			if (channelData.messageQueue.size() == 0) {
				return null;
			}
			// dequeue oldest message
			message = channelData.messageQueue.remove(0);
			MessageType messageType = message.getMessageHeader()
					.getMessageType();
			// prepare synchronous message processing
			switch (messageType) {
			case GET_CONFIGURATION:
			case SET_CONFIGURATION:
			case RESET_CONFIGURATION:
				channelData.sendingMessages.add(message);
				logSendingMessage(message);
			}
			// process received message
			Message response = null;
			try {
				switch (messageType) {
				case GET_CONFIGURATION:
					GetConfiguration gconf = (GetConfiguration) message;
					List<Configuration> gconfResponses = new ArrayList<>();
					GetConfigurationResponse gcr = new GetConfigurationResponse(
							new MessageHeader(message.getMessageHeader()
									.getId()), gconfResponses);
					try {
						for (Type confType : gconf.getTypes()) {
							List<Configuration> configs = null;
							lock.unlock();
							try {
								// while the execution is running neither a
								// further method of the controller must be
								// called nor the connection must be closed!
								configs = channelData.controller
										.getConfiguration(confType,
												gconf.getPinId());
							} finally {
								lock.lock();
							}
							if (configs != null) {
								gconfResponses.addAll(configs);
							}
						}
					} catch (ParameterException e) {
						gcr.setException(e);
					}
					response = gcr;
					break;
				case SET_CONFIGURATION:
					SetConfiguration sconf = (SetConfiguration) message;
					SetConfigurationResponse scr = new SetConfigurationResponse(
							new MessageHeader(message.getMessageHeader()
									.getId()));
					try {
						lock.unlock();
						try {
							// while the execution is running neither a
							// further method of the controller must be
							// called nor the connection must be closed!
							channelData.controller.setConfiguration(sconf
									.getConfiguration());
						} finally {
							lock.lock();
						}
					} catch (ParameterException e) {
						scr.setException(e);
					}
					response = scr;
					break;
				case RESET_CONFIGURATION:
					lock.unlock();
					try {
						// while the execution is running neither a
						// further method of the controller must be
						// called nor the connection must be closed!
						channelData.controller.resetConfiguration();
					} finally {
						lock.lock();
					}
					response = new ResetConfigurationResponse(
							new MessageHeader(message.getMessageHeader()
									.getId()));
					break;
				case KEEP_ALIVE:
				case CONNECTION_ATTEMPTED:
				case STATE_CHANGED:
					// forward the message because it is not processed here
					break;
				}
				// finish synchronous message processing
				if (response != null) {
					logReceivedMessage(response);
					channelData.sendingMessages.remove(message);
					// replace request with response for return
					message = response;
				}
			} catch (Exception e) {
				channelData.sendingMessages.remove(message);
				exception = new GPIOException("Processing of " + messageType
						+ " message failed", e);
			}
			if (exception != null) {
				// collect pending data
				pendingReceivedData = getPendingReceivedData(channelData);
				pendingSendingData = getPendingSendingData(channelData);
				// close the channel
				try {
					closeChannel(channel);
				} catch (Exception e1) {
					// only log this exception because the first occurred
					// exception is thrown
					log.log(Level.SEVERE, "Cannot close channel", e1);
				}
			}
			// save event handler locally to send events outside the
			// synchronized block
			eventHandler = channelData.eventHandler;
		} finally {
			lock.unlock();
		}
		if (exception != null) {
			// fire "close" event incl. exception and pending data
			eventHandler.channelClosed(new GPIOChannelClosedEvent(
					null/* serverChannel */, channel, pendingSendingData,
					pendingReceivedData, exception));
			throw exception;
		}
		// fire "dataReceived" event
		eventHandler.dataReceived(new GPIODataReceivedNotifyEvent(
				null/* serverChannel */, channel));
		return message;
	}

	/**
	 * See {@link TCPServerMultiplexed#requestClosingChannel(SelectableChannel)}
	 * 
	 * @throws GPIOConnectionException
	 */
	public void requestClosingChannel(SelectableChannel channel)
			throws GPIOConnectionException {
		ChannelData channelData = null;
		List<Message> pendingReceivedData = null;
		List<Message> pendingSendingData = null;
		lock.lock();
		try {
			// close the channel and remove it from the local list
			channelData = closeChannel((SocketChannel) channel);
			if (channelData != null) {
				// collect pending data
				pendingReceivedData = getPendingReceivedData(channelData);
				pendingSendingData = getPendingSendingData(channelData);
			}
		} catch (Exception e) {
			throw new GPIOConnectionException("Cannot close channel", e);
		} finally {
			lock.unlock();
		}
		if (channelData != null) {
			// send close event
			channelData.eventHandler
					.channelClosed(new GPIOChannelClosedEvent(
							null/* serverChannel */, (SocketChannel) channel,
							pendingSendingData, pendingReceivedData, null /* exception */));
		}
	}

	private List<Message> getPendingReceivedData(ChannelData channelData) {
		if (channelData.messageQueue.size() > 0) {
			return channelData.messageQueue;
		}
		return null;
	}

	private List<Message> getPendingSendingData(ChannelData channelData) {
		if (channelData.sendingMessages.size() > 0) {
			return channelData.sendingMessages;
		}
		return null;
	}

	/**
	 * Closes a channel and removes it from the local list.
	 * 
	 * @param channel
	 * @return The removed channel
	 * @throws ConnectionException
	 * @throws ImplementationException
	 * @throws ServiceFactoryException
	 * @throws IOException
	 */
	private ChannelData closeChannel(SocketChannel channel)
			throws ConnectionException, ImplementationException,
			ServiceFactoryException, IOException {
		// remove channel from local list
		ChannelData channelData = channels.remove(channel);
		if (channelData != null) {
			// close the connection to the GPIO controller and release the GPIO
			// controller instance
			lock.unlock();
			try {
				channelData.controller.closeConnection();
			} finally {
				lock.lock();
			}
			serviceFactory.release(channelData.controller);
			// close the channel
			channel.close();
			// trigger the throwing of an exception for all threads waiting
			// for data from this channel
			channelData.messageQueueContainsMessage.signalAll();
		}
		return channelData;
	}

	private void logSendingMessage(Message message) {
		if (log.isLoggable(Level.INFO)) {
			MessageHeader header = message.getMessageHeader();
			log.log(Level.INFO, "Sending {0} (id={1})", new Object[]{ header.getMessageType(), header.getId() });
		}
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, message.toString());
		}
		tracing(message);
	}

	private void logReceivedMessage(Message message) {
		if (log.isLoggable(Level.INFO)) {
			MessageHeader header = message.getMessageHeader();
			log.log(Level.INFO, "Received {0} (id={1})", new Object[] { header.getMessageType(), header.getId() });
		}
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, message.toString());
		}
		tracing(message);
	}

	private void tracing(Message message) {
		if (log.isLoggable(Level.FINER)) {
			JsonSerializer serializer = new JsonSerializer(Message.class);
			serializer.setPrettyPrint(true);
			serializer.addSerializerMixIns(new GPIOJacksonMixIns());
			try {
				log.log(Level.FINER, serializer.serialize(message));
			} catch (IOException e) {
				log.log(Level.SEVERE, "Cannot serialize the message to JSON", e);
			}
		}
	}
}
