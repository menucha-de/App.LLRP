package havis.llrpservice.sbc.rfc;

import java.io.IOException;
import java.lang.Thread.State;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.device.rf.RFConsumer;
import havis.device.rf.RFDevice;
import havis.device.rf.capabilities.Capabilities;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.exception.CommunicationException;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.TagOperation;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.sbc.rfc.event.RFCChannelClosedEvent;
import havis.llrpservice.sbc.rfc.event.RFCChannelOpenedEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataReceivedNotifyEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataSentEvent;
import havis.llrpservice.sbc.rfc.json.RFCJacksonMixIns;
import havis.llrpservice.sbc.rfc.message.ConnectionAttempted;
import havis.llrpservice.sbc.rfc.message.Execute;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.sbc.rfc.message.GetCapabilities;
import havis.llrpservice.sbc.rfc.message.GetCapabilitiesResponse;
import havis.llrpservice.sbc.rfc.message.GetConfiguration;
import havis.llrpservice.sbc.rfc.message.GetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.sbc.rfc.message.GetOperationsResponse;
import havis.llrpservice.sbc.rfc.message.KeepAlive;
import havis.llrpservice.sbc.rfc.message.Message;
import havis.llrpservice.sbc.rfc.message.MessageHeader;
import havis.llrpservice.sbc.rfc.message.MessageType;
import havis.llrpservice.sbc.rfc.message.ResetConfigurationResponse;
import havis.llrpservice.sbc.rfc.message.Response;
import havis.llrpservice.sbc.rfc.message.SetConfiguration;
import havis.llrpservice.sbc.rfc.message.SetConfigurationResponse;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactoryException;
import havis.llrpservice.server.platform.TimeStamp;
import havis.util.platform.Platform;

/**
 * A RFC server implementation. It allows the opening of multiple channels to
 * several servers.
 * <p>
 * While a channel is being opened with
 * {@link #requestOpeningChannel(String, int, RFCEventHandler)} it is assigned
 * to an event handler which must implement the interface
 * {@link RFCEventHandler}. The server fires events to the event handler after
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
public class RFCClientMultiplexed {

	private static final Logger log = Logger.getLogger(RFCClientMultiplexed.class.getName());

	public static final int NO_TIMEOUT = -1;
	public static final int RETURN_IMMEDIATELY = 0;

	private final ServiceFactory<RFDevice> serviceFactory;
	private long openTimeout;
	private long callbackTimeout;
	private Platform platform;

	private ExecutorService threadPool = Executors.newFixedThreadPool(1);

	private final ReentrantLock lock = new ReentrantLock();
	private final Map<SocketChannel, ChannelData> channels = new HashMap<>();

	private class ChannelData {
		RFDevice controller;
		RFCEventHandler eventHandler;
		List<Message> messageQueue = new ArrayList<>();
		Condition messageQueueContainsMessage;
		List<Message> sendingMessages = new ArrayList<>();
		Future<Exception> executionFuture = null;
		GetOperationsResponse getOperationsResponse = null;
		Condition getOperationsResponseExists;
		RFCException getOperationsException = null;

		public void enqueueMessage(Message message) {
			messageQueue.add(message);
			messageQueueContainsMessage.signal();
		}

		public void setOperationsResponse(GetOperationsResponse message) {
			getOperationsResponse = message;
			getOperationsResponseExists.signal();
		}
	}

	private class RFConsumerImpl implements RFConsumer {

		private final SocketChannel channel;

		public RFConsumerImpl(SocketChannel channel) {
			this.channel = channel;
		}

		@Override
		public List<TagOperation> getOperations(TagData tagData) {
			GetOperations request = new GetOperations(
					new MessageHeader(IdGenerator.getNextLongId()), tagData);
			logReceivedMessage(request);
			ChannelData channelData = null;
			lock.lock();
			try {
				channelData = channels.get(channel);
				channelData.sendingMessages.add(request);
				// add GetOperations message to local message list
				// for delivery via awaitReceivedData
				channelData.enqueueMessage(request);
				// wait for GetOperationsResponse message
				while (channelData.getOperationsResponse == null) {
					try {
						if (!channelData.getOperationsResponseExists.await(callbackTimeout,
								TimeUnit.MILLISECONDS)) {
							channelData.getOperationsException = new RFCTimeoutException(
									"Time out after " + callbackTimeout + " ms while waiting for "
											+ MessageType.GET_OPERATIONS_RESPONSE + " message");
							return new ArrayList<>();
						}
					} catch (InterruptedException e) {
						channelData.getOperationsException = new RFCException("Waiting for "
								+ MessageType.GET_OPERATIONS_RESPONSE + " message was interrupted",
								e);
						return new ArrayList<>();
					}
				}
				// remove the response from the channel data and return it
				List<TagOperation> operations = channelData.getOperationsResponse.getOperations();
				channelData.getOperationsResponse = null;
				logSendingMessage(new GetOperationsResponse(
						new MessageHeader(IdGenerator.getNextLongId()), operations));
				return operations;
			} finally {
				if (channelData != null) {
					channelData.sendingMessages.remove(request);
				}
				lock.unlock();
			}
		}

		@Override
		public void keepAlive() {
			enqueueMessage(new KeepAlive(new MessageHeader(IdGenerator.getNextLongId())));
		}

		@Override
		public void connectionAttempted() {
			enqueueMessage(new ConnectionAttempted(new MessageHeader(IdGenerator.getNextLongId())));
		}

		/**
		 * Enqueues a received message to the local list and triggers the
		 * processing of the message.
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
	 *            time out for {@link RFDevice#openConnection(RFConsumer, int)}
	 *            call in ms
	 * @param callbackTimeout
	 *            time out for callbacks like {@link MessageType#GET_OPERATIONS}
	 *            in ms
	 * @param serviceFactory
	 * @param platform
	 */
	public RFCClientMultiplexed(ServiceFactory<RFDevice> serviceFactory, int openTimeout,
			int callbackTimeout, Platform platform) {
		this.serviceFactory = serviceFactory;
		this.openTimeout = openTimeout;
		this.callbackTimeout = callbackTimeout;
		this.platform = platform;
	}

	/**
	 * Requests the opening of a channel to an address/port. The method
	 * {@link RFCEventHandler#channelOpened(RFCChannelOpenedEvent)} of the event
	 * handler is called after the channel has been opened.
	 * 
	 * @param addr
	 * @param port
	 * @param eventHandler
	 * @throws ImplementationException
	 * @throws ParameterException
	 * @throws CommunicationException
	 * @throws ConnectionException
	 * @throws IOException
	 */
	public void requestOpeningChannel(String addr, int port, RFCEventHandler eventHandler)
			throws RFCException {
		long start = System.currentTimeMillis();
		ChannelData channelData = new ChannelData();
		SocketChannel channel = null;
		lock.lock();
		try {

			try {
				// instantiate the RF controller
				channelData.controller = serviceFactory.getService(addr, port, openTimeout);
				channelData.eventHandler = eventHandler;
				channelData.messageQueueContainsMessage = lock.newCondition();
				channelData.getOperationsResponseExists = lock.newCondition();
				// create a channel
				channel = SocketChannel.open();
				channels.put(channel, channelData);
				// open a connection to the RF controller
				long remainingTimeout = openTimeout - (System.currentTimeMillis() - start);
				lock.unlock();
				try {
					// while the execution is running neither a
					// further method of the controller must be
					// called nor the connection must be closed!
					channelData.controller.openConnection(new RFConsumerImpl(channel),
							(int) (remainingTimeout <= 0 ? 1 : remainingTimeout));
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
				throw new RFCConnectionException("Cannot create a connection to the RF controller",
						e);
			}
		} finally {
			lock.unlock();
		}
		// send open event
		eventHandler.channelOpened(new RFCChannelOpenedEvent(null/* serverChannel */, channel));
	}

	/**
	 * Sends a message to a channel.
	 * <p>
	 * The channel must be opened before with
	 * {@link #requestOpeningChannel(String, int, RFCEventHandler)}.
	 * </p>
	 * 
	 * @param channel
	 * @param message
	 */
	public void requestSendingData(SocketChannel channel, final Message message) {
		ChannelData channelData = null;
		RFCEventHandler eventHandler = null;
		lock.lock();
		try {
			channelData = channels.get(channel);
			// save event handler locally to sent events outside the
			// synchronized block
			eventHandler = channelData.eventHandler;
			switch (message.getMessageHeader().getMessageType()) {
			case GET_CAPABILITIES:
			case GET_CONFIGURATION:
			case SET_CONFIGURATION:
			case RESET_CONFIGURATION:
				// add message to the channel data
				// The message is being processed synchronously when its
				// response is expected in awaitReceivedData.
				channelData.enqueueMessage(message);
				break;
			case EXECUTE:
				final Execute ex = (Execute) message;
				final ChannelData cd = channelData;
				cd.sendingMessages.add(message);
				// callback "getOperations" must be processed while execution
				// => start the execution in an own thread and save the future
				channelData.executionFuture = threadPool.submit(new Callable<Exception>() {

					@Override
					public Exception call() throws Exception {
						Response response = null;
						try {
							logSendingMessage(message);
							// while the execution is running neither a
							// further method of the controller must be
							// called nor the connection must be closed!
							List<TagData> tagData = cd.controller.execute(ex.getAntennas(),
									ex.getFilters(), ex.getOperations());
							response = new ExecuteResponse(
									new MessageHeader(ex.getMessageHeader().getId()), tagData,
									new TimeStamp(platform));
							logReceivedMessage(response);
						} catch (Exception e) {
							response = new ExecuteResponse(
									new MessageHeader(ex.getMessageHeader().getId()),
									null /* tagData */, new TimeStamp(platform));
							return e;
						} finally {
							lock.lock();
							try {
								cd.sendingMessages.remove(message);
								// add response to the channel data
								// (if an exception has been occurred
								// the delivering of the error
								// is triggered with an empty response)
								cd.enqueueMessage(response);
							} finally {
								lock.unlock();
							}
						}
						return null;
					}
				});
				break;
			case GET_OPERATIONS_RESPONSE:
				// set operations response to the channel data and release the
				// waiting callback
				channelData.setOperationsResponse((GetOperationsResponse) message);
				break;
			default:
				break;
			}
		} finally {
			lock.unlock();
		}
		// fire "dataSent" event
		// no serialization necessary => no serialization exception and no
		// pending data
		eventHandler.dataSent(new RFCDataSentEvent(null/* serverChannel */, channel,
				message.getMessageHeader().getId(), /* pendingData */
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
	 * @return RFC message or <code>null</code> if {@link #NO_TIMEOUT} is used
	 *         and no message has been received
	 * @throws InterruptedException
	 * @throws RFCException
	 */
	public Message awaitReceivedData(SocketChannel channel, long timeout)
			throws InterruptedException, RFCException {
		Message message = null;
		RFCEventHandler eventHandler = null;
		RFCException exception = null;
		List<Message> pendingSendingData = null;
		List<Message> pendingReceivedData = null;
		lock.lock();
		try {
			ChannelData channelData = channels.get(channel);
			while (channelData.messageQueue.size() == 0 && timeout != 0) {
				if (timeout < 0) {
					channelData.messageQueueContainsMessage.await();
				} else if (!channelData.messageQueueContainsMessage.await(timeout,
						TimeUnit.MILLISECONDS)) {
					throw new RFCTimeoutException("Time out after " + timeout
							+ " ms while waiting for received messages");
				}
				// if the channel has been closed while waiting for data
				if (!channels.containsKey(channel)) {
					throw new RFCUnknownChannelException("Closed channel: " + channel);
				}
			}
			if (channelData.messageQueue.size() == 0) {
				return null;
			}
			// dequeue oldest message
			message = channelData.messageQueue.remove(0);
			MessageType messageType = message.getMessageHeader().getMessageType();
			// prepare synchronous message processing
			switch (messageType) {
			case GET_CAPABILITIES:
			case GET_CONFIGURATION:
			case SET_CONFIGURATION:
			case RESET_CONFIGURATION:
				channelData.sendingMessages.add(message);
				logSendingMessage(message);
				break;
			default:
				break;
			}
			// process received message
			Message response = null;
			try {
				switch (messageType) {
				case GET_CAPABILITIES:
					GetCapabilities gc = (GetCapabilities) message;
					List<Capabilities> gcResponses = new ArrayList<>();
					for (CapabilityType capType : gc.getTypes()) {
						lock.unlock();
						try {
							// while the execution is running neither a
							// further method of the controller must be
							// called nor the connection must be closed!
							gcResponses.addAll(channelData.controller.getCapabilities(capType));
						} finally {
							lock.lock();
						}
					}
					response = new GetCapabilitiesResponse(
							new MessageHeader(message.getMessageHeader().getId()), gcResponses);
					break;
				case GET_CONFIGURATION:
					GetConfiguration gconf = (GetConfiguration) message;
					List<Configuration> gconfResponses = new ArrayList<>();
					for (ConfigurationType confType : gconf.getTypes()) {
						lock.unlock();
						try {
							// while the execution is running neither a
							// further method of the controller must be
							// called nor the connection must be closed!
							gconfResponses.addAll(channelData.controller.getConfiguration(confType,
									gconf.getAntennaID(), (short) 0 /* gpiPort */,
									(short) 0 /* gpoPort */));
						} finally {
							lock.lock();
						}
					}
					response = new GetConfigurationResponse(
							new MessageHeader(message.getMessageHeader().getId()), gconfResponses);
					break;
				case SET_CONFIGURATION:
					SetConfiguration sconf = (SetConfiguration) message;
					lock.unlock();
					try {
						// while the execution is running neither a
						// further method of the controller must be
						// called nor the connection must be closed!
						channelData.controller.setConfiguration(sconf.getConfiguration());
					} finally {
						lock.lock();
					}
					response = new SetConfigurationResponse(
							new MessageHeader(message.getMessageHeader().getId()));
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
							new MessageHeader(message.getMessageHeader().getId()));
					break;
				case EXECUTE_RESPONSE:
					// wait for the end of the execution thread and get a
					// possible exception (no time out for waiting because the
					// this EXECUTE_RESPONSE has been enqueued directly before
					// the thread finishes)
					Exception threadException = channelData.executionFuture.get();
					if (threadException != null) {
						exception = new RFCException(
								"Processing of " + messageType + " message failed",
								threadException);
					}
					// get exception from processing of GET_OPERATIONS
					// callback
					if (channelData.getOperationsException != null) {
						exception = channelData.getOperationsException;
						channelData.getOperationsException = null;
					}
					break;
				case GET_OPERATIONS:
				case KEEP_ALIVE:
				case CONNECTION_ATTEMPTED:
					// forward the message because it is not processed here
					break;
				default:
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
				exception = new RFCException("Processing of " + messageType + " message failed", e);
			}
			if (exception != null) {
				// collect pending data
				pendingReceivedData = getPendingReceivedData(channelData);
				pendingSendingData = getPendingSendingData(channelData);
				// close the connection to the controller
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
			eventHandler.channelClosed(new RFCChannelClosedEvent(null/* serverChannel */, channel,
					pendingSendingData, pendingReceivedData, exception));
			throw exception;
		}
		// fire "dataReceived" event
		eventHandler.dataReceived(new RFCDataReceivedNotifyEvent(null/* serverChannel */, channel));
		return message;
	}

	/**
	 * See {@link TCPServerMultiplexed#requestClosingChannel(SelectableChannel)}
	 * 
	 * @throws RFCConnectionException
	 */
	public void requestClosingChannel(SelectableChannel channel) throws RFCConnectionException {
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
			throw new RFCConnectionException("Cannot close channel", e);
		} finally {
			lock.unlock();
		}
		if (channelData != null) {
			// send close event
			channelData.eventHandler.channelClosed(
					new RFCChannelClosedEvent(null/* serverChannel */, (SocketChannel) channel,
							pendingSendingData, pendingReceivedData, /* exception */
							null));
		}
	}

	private List<Message> getPendingReceivedData(ChannelData channelData) {
		if (channelData.messageQueue.size() > 0) {
			return channelData.messageQueue;
		}
		return null;
	}

	private List<Message> getPendingSendingData(ChannelData channelData) {
		List<Message> pendingData = null;
		if (channelData.sendingMessages.size() > 0 || channelData.getOperationsResponse != null) {
			pendingData = channelData.sendingMessages.size() > 0 ? channelData.sendingMessages
					: new ArrayList<Message>();
			if (channelData.getOperationsResponse != null) {
				pendingData.add(channelData.getOperationsResponse);
			}
		}
		return pendingData;
	}

	/**
	 * Closes a channel and removes it from the local list.
	 * 
	 * @param channel
	 * @return The closed channel
	 * @throws ConnectionException
	 * @throws ServiceFactoryException
	 * @throws IOException
	 */
	private ChannelData closeChannel(SocketChannel channel)
			throws ConnectionException, ServiceFactoryException, IOException {
		// remove channel from local list
		ChannelData channelData = channels.remove(channel);
		if (channelData != null) {
			// close the connection to the RF controller and release the RF
			// controller instance
			lock.unlock();
			try {
				// while the execution is running neither a
				// further method of the controller must be
				// called nor the connection must be closed!
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
			log.log(Level.INFO, "Sending {0} (id={1})",
					new Object[] { header.getMessageType(), header.getId() });
		}
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, message.toString());
		}
		tracing(message);
	}

	private void logReceivedMessage(Message message) {
		if (log.isLoggable(Level.INFO)) {
			MessageHeader header = message.getMessageHeader();
			log.log(Level.INFO, "Received {0} (id={1})",
					new Object[] { header.getMessageType(), header.getId() });
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
			serializer.addSerializerMixIns(new RFCJacksonMixIns());
			try {
				log.log(Level.FINER, serializer.serialize(message));
			} catch (IOException e) {
				log.log(Level.SEVERE, "Cannot serialize the message to JSON", e);
			}
		}
	}
}
