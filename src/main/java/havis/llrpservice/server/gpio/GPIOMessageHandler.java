package havis.llrpservice.server.gpio;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.device.io.Configuration;
import havis.device.io.IOConfiguration;
import havis.device.io.IODevice;
import havis.device.io.Type;
import havis.llrpservice.common.concurrent.EventPipe;
import havis.llrpservice.common.concurrent.EventPipes;
import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.sbc.gpio.GPIOClientMultiplexed;
import havis.llrpservice.sbc.gpio.GPIOConnectionException;
import havis.llrpservice.sbc.gpio.GPIOEventHandler;
import havis.llrpservice.sbc.gpio.GPIOException;
import havis.llrpservice.sbc.gpio.GPIOInvalidPortNumException;
import havis.llrpservice.sbc.gpio.GPIOUnknownChannelException;
import havis.llrpservice.sbc.gpio.event.GPIOChannelClosedEvent;
import havis.llrpservice.sbc.gpio.event.GPIOChannelOpenedEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataReceivedNotifyEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataSentEvent;
import havis.llrpservice.sbc.gpio.message.GetConfiguration;
import havis.llrpservice.sbc.gpio.message.GetConfigurationResponse;
import havis.llrpservice.sbc.gpio.message.Message;
import havis.llrpservice.sbc.gpio.message.MessageHeader;
import havis.llrpservice.sbc.gpio.message.MessageType;
import havis.llrpservice.sbc.gpio.message.ResetConfiguration;
import havis.llrpservice.sbc.gpio.message.SetConfiguration;
import havis.llrpservice.sbc.service.ReflectionServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.GPIOMessageEvent;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.GpioPortProperties;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.ReflectionType;

/**
 * The GPIOMessageHandler handles the communication to a GPIO controller.
 * <p>
 * A connection to the GPIO controller is opened with
 * {@link #open(ExecutorService)} and closed with {@link #close()}. Before the
 * message handler can be opened again the method {@link #run} must have been
 * started and stopped in a separate thread.
 * </p>
 * <p>
 * Requests for the GPIO controller can be enqueued with methods like
 * {@link #requestExecution(boolean, List)} or
 * {@link #requestConfiguration(List, Short, Short)}. The sent and received GPIO
 * messages to/from the controller are put to an event queue.
 * </p>
 */
public class GPIOMessageHandler implements Runnable {

	private static final Logger log = Logger.getLogger(GPIOMessageHandler.class.getName());

	// properties from configuration
	private final int openCloseTimeout;
	private final AddressGroup llrpAddress;

	private final GPIOClientMultiplexed gpioClient;
	// the callback for the GPIO client
	private final EventHandler gpioEventHandler = new EventHandler();
	// the opened channel to the GPIO controller
	private SocketChannel gpioChannel;

	private final EventQueue eventQueue;

	private final List<GPIOMessageHandlerListener> listeners = new CopyOnWriteArrayList<>();

	private final List<InternalRequest> requestQueue = new ArrayList<>();
	private InternalRequest executingRequest = null;

	private final ReentrantLock lock = new ReentrantLock();

	private enum RunLatchEvent {
		OPENED, CLOSED
	}

	private EventPipe<RunLatchEvent> runLatch = new EventPipe<>(lock);

	private Future<?> thread;
	private boolean hasThreadCompleted;

	class InternalRequest {
		MessageType type;
		Object request;

		// GET_CONFIGURATION
		short gpiPortNum;
		short gpoPortNum;

		InternalRequest(MessageType type, Object request) {
			this.type = type;
			this.request = request;
		}
	}

	/**
	 * The callback implementation for a GPIO client.
	 */
	private class EventHandler implements GPIOEventHandler {
		private EventPipes latches = new EventPipes(new ReentrantLock());

		public SocketChannel awaitChannelOpened(long timeout)
				throws InterruptedException, TimeoutException {
			List<GPIOChannelOpenedEvent> events = latches.await(GPIOChannelOpenedEvent.class,
					timeout);
			return events.size() == 0 ? null : events.get(0).getChannel();
		}

		public GPIOException awaitChannelClosed(long timeout)
				throws InterruptedException, TimeoutException {
			List<GPIOChannelClosedEvent> events = latches.await(GPIOChannelClosedEvent.class,
					timeout);
			return events.size() == 0 ? null : events.get(0).getException();
		}

		@Override
		public void channelOpened(GPIOChannelOpenedEvent event) {
			latches.fire(event);
		}

		@Override
		public void dataSent(GPIODataSentEvent event) {
			// GPIOClientMultiplexed does not sent this event with an exception
			// channelClosed is used instead
		}

		@Override
		public void dataReceived(GPIODataReceivedNotifyEvent event) {
		}

		@Override
		public void channelClosed(GPIOChannelClosedEvent event) {
			latches.fire(event);
		}

	}

	/**
	 * @param serverConfiguration
	 * @param instanceConfiguration
	 * @param eventQueue
	 *            event queue for sent and received GPIO messages (enqueued as
	 *            {@link GPIOMessageEvent})
	 * @param serviceFactory
	 *            The service factory for GPIO controllers provided by the OSGi
	 *            platform. If it is <code>null</code> then the LLRP
	 *            configuration must provide the properties for creating a GPIO
	 *            controller via the Java Reflection API.
	 * @throws EntityManagerException
	 * @throws ConfigurationException
	 * @throws PersistenceException
	 * @throws ClassNotFoundException
	 *             the creating of a GPIO controller via Java Reflection API
	 *             failed
	 * @throws MissingServiceFactoryException
	 */
	public GPIOMessageHandler(ServerConfiguration serverConfiguration,
			ServerInstanceConfiguration instanceConfiguration, EventQueue eventQueue,
			ServiceFactory<IODevice> serviceFactory)
			throws EntityManagerException, ConfigurationException, PersistenceException,
			ClassNotFoundException, MissingServiceFactoryException {
		// create config analyser
		Entity<LLRPServerConfigurationType> serverConfigEntity = serverConfiguration.acquire();
		Entity<LLRPServerInstanceConfigurationType> instanceConfigEntity = instanceConfiguration
				.acquire();
		GPIOConfigAnalyser configAnalyser = new GPIOConfigAnalyser(serverConfigEntity.getObject());
		configAnalyser.setServerInstanceConfig(instanceConfigEntity.getObject());
		instanceConfiguration.release(instanceConfigEntity, false /* write */);
		serverConfiguration.release(serverConfigEntity, false /* write */);

		llrpAddress = configAnalyser.getAddress();
		GpioPortProperties gpioPortProperties = configAnalyser.getGPIOPortProperties();
		openCloseTimeout = gpioPortProperties.getOpenCloseTimeout();

		if (gpioPortProperties.ifReflection()) {
			ReflectionType reflectionProps = gpioPortProperties.getReflection();
			String addressSetterMethodName = reflectionProps.getAddressSetterMethodName();
			if (addressSetterMethodName != null) {
				addressSetterMethodName = addressSetterMethodName.trim();
			}
			serviceFactory = new ReflectionServiceFactory<>(
					reflectionProps.getControllerClassName().trim(), addressSetterMethodName);
		} else if (gpioPortProperties.ifOSGi() && serviceFactory == null) {
			throw new MissingServiceFactoryException("Missing OSGi service factory");
		}
		// create a GPIO client using an own instance for the GPIO interface
		gpioClient = new GPIOClientMultiplexed(serviceFactory, openCloseTimeout);

		this.eventQueue = eventQueue;
	}

	/**
	 * Adds a listener.
	 * 
	 * @param listener
	 */
	public void addListener(GPIOMessageHandlerListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(GPIOMessageHandlerListener listener) {
		List<GPIOMessageHandlerListener> removed = new ArrayList<>();
		for (GPIOMessageHandlerListener entry : listeners) {
			if (listener == entry) {
				removed.add(entry);
			}
		}
		listeners.removeAll(removed);
	}

	/**
	 * Opens the message handler synchronously. If it is opened and
	 * {@link #run()} has been started a
	 * {@link GPIOMessageHandlerListener#opened()} event is sent to registered
	 * listeners.
	 * 
	 * @param threadPool
	 * @throws InterruptedException
	 * @throws TimeoutException
	 * @throws GPIOException
	 */
	public void open(ExecutorService threadPool)
			throws InterruptedException, TimeoutException, GPIOException {
		thread = threadPool.submit(this);
		lock.lock();
		try {
			hasThreadCompleted = false;
			// request the opening of the channel
			gpioClient.requestOpeningChannel(llrpAddress.getHost(), llrpAddress.getPort(),
					gpioEventHandler);
			// wait for opened channel
			try {
				gpioChannel = gpioEventHandler.awaitChannelOpened(openCloseTimeout);
			} catch (TimeoutException e) {
				throw new TimeoutException(
						"Unable to open the channel within " + openCloseTimeout + " ms");
			}
			// start handling of messages
			runLatch.fire(RunLatchEvent.OPENED);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Closes the message handler synchronously. The connection to the GPIO
	 * controller is canceled and method {@link #run} is stopped. Before
	 * {@link #run} stops a
	 * {@link GPIOMessageHandlerListener#closed(List, Throwable)} event is sent
	 * to registered listeners.
	 * 
	 * @throws GPIOConnectionException
	 * @throws GPIOException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 *             time out for closing the channel to the client
	 * @throws ExecutionException
	 */
	public void close() throws GPIOConnectionException, GPIOException, InterruptedException,
			TimeoutException, ExecutionException {
		if (thread == null) {
			return;
		}
		long end = System.currentTimeMillis() + openCloseTimeout;
		long timeout = 0;
		lock.lock();
		try {
			if (!hasThreadCompleted) {
				// enqueue a CLOSED event (the thread may wait for an open
				// event)
				runLatch.fire(RunLatchEvent.CLOSED);
			}
			if (gpioChannel != null) {
				// close the channel to the client (a blocked "run" method
				// is released)
				gpioClient.requestClosingChannel(gpioChannel);
				try {
					GPIOException exception = gpioEventHandler.awaitChannelClosed(openCloseTimeout);
					if (exception != null) {
						throw exception;
					}
				} catch (TimeoutException e) {
					throw new TimeoutException(
							"Unable to close the channel within " + openCloseTimeout + " ms");
				}
				gpioChannel = null;
			}
		} finally {
			lock.unlock();
		}
		// wait for the termination of the thread
		timeout = end - System.currentTimeMillis();
		thread.get(timeout < 1 ? 1 : timeout, TimeUnit.MILLISECONDS);
		thread = null;
	}

	/**
	 * Requests configuration data.
	 * <p>
	 * The request is sent to the event queue directly before it will be
	 * processed. The response is sent to the event queue after the message has
	 * been received.
	 * </p>
	 * 
	 * @param confTypes
	 * @param gpiPortNum
	 *            The GPI port number for {@link Type#IO}. If it is 0 then the
	 *            IO configuration for all GPI ports is returned.
	 * @param gpoPortNum
	 *            The GPO port number for {@link Type#IO}. If it is 0 then the
	 *            IO configuration for all GPO ports is returned.
	 * @return message identifier
	 * @throws GPIOException
	 */
	public long requestConfiguration(List<Type> confTypes, Short gpiPortNum, Short gpoPortNum)
			throws GPIOException {
		lock.lock();
		try {
			short pinId = 0;
			if (gpiPortNum != null) {
				// if only GPI ports are requested
				if (gpoPortNum == null) {
					pinId = gpiPortNum;
				}
			} // else if only GPO ports are requested
			else if (gpoPortNum != null) {
				pinId = gpoPortNum;
			}
			long id = IdGenerator.getNextLongId();
			InternalRequest request = new InternalRequest(MessageType.GET_CONFIGURATION,
					new GetConfiguration(new MessageHeader(id), confTypes, pinId));
			request.gpiPortNum = gpiPortNum == null ? -1 : gpiPortNum;
			request.gpoPortNum = gpoPortNum == null ? -1 : gpoPortNum;
			// enqueue request
			requestQueue.add(request);
			// if no request is executed then start the next
			// available request
			processRequest();
			return id;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Requests the setting of configuration data. A reset can be performed
	 * before the configuration is changed.
	 * <p>
	 * The requests are sent to the event queue directly before they will be
	 * processed. The responses are sent to the event queue after the messages
	 * have been received.
	 * </p>
	 * 
	 * @param reset
	 * @param configurations
	 * @return message identifier
	 * @throws GPIOException
	 */
	public long requestExecution(boolean reset, List<Configuration> configurations)
			throws GPIOException {
		lock.lock();
		try {
			// enqueue a request
			if (reset) {
				requestQueue.add(new InternalRequest(MessageType.RESET_CONFIGURATION,
						new ResetConfiguration(new MessageHeader(IdGenerator.getNextLongId()))));
			}
			long id = IdGenerator.getNextLongId();
			requestQueue.add(new InternalRequest(MessageType.SET_CONFIGURATION,
					new SetConfiguration(new MessageHeader(id), configurations)));
			// if no request is executed then start the next
			// available request
			processRequest();
			return id;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Runs the message handler. The methods blocks until the message handler is
	 * closed with {@link #close()} or an exception is thrown while an incoming
	 * message is being processed.
	 */
	@Override
	public void run() {
		Throwable throwable = null;
		try {
			// block until the "open" or "close" method has been called
			List<RunLatchEvent> events = runLatch.await(EventPipe.NO_TIMEOUT);
			// if message handler is closed
			if (events.contains(RunLatchEvent.CLOSED)) {
				return;
			}
			// fire "opened" events
			for (GPIOMessageHandlerListener listener : listeners) {
				listener.opened();
			}
			while (true) {
				// wait for next message
				Message message = gpioClient.awaitReceivedData(gpioChannel,
						GPIOClientMultiplexed.NO_TIMEOUT);
				lock.lock();
				try {
					// process incoming message
					processIncomingMessage(message);
					// if no request is executed then start the next
					// available request
					processRequest();
				} finally {
					lock.unlock();
				}
			}
		} catch (GPIOUnknownChannelException e) {
			lock.lock();
			try {
				// if the channel has been closed from GPIOClientMultiplexed
				if (gpioChannel != null) {
					// if the channel has been closed a close event must have
					// been sent -> forward the exception of the close event
					try {
						throwable = gpioEventHandler.awaitChannelClosed(openCloseTimeout);
					} catch (Exception ex) {
						log.log(Level.SEVERE, "Cannot get the cause for the closed channel", e);
					}
					gpioChannel = null;
				}
			} finally {
				lock.unlock();
			}
		} catch (Throwable t) {
			throwable = t;
		} finally {
			// remove pending requests
			List<Object> pendingRequests = new ArrayList<>();
			for (InternalRequest request : requestQueue) {
				pendingRequests.add(request.request);
			}
			requestQueue.clear();
			executingRequest = null;
			if (listeners.size() == 0) {
				if (throwable != null) {
					log.log(Level.SEVERE, "Execution stopped with " + pendingRequests.size()
							+ " pending requests due to an exception", throwable);
				}
			} else {
				// fire "closed" events
				for (GPIOMessageHandlerListener listener : listeners) {
					listener.closed(pendingRequests, throwable);
				}
			}
			// remove existing events
			lock.lock();
			try {
				runLatch.await(EventPipe.RETURN_IMMEDIATELY);
				hasThreadCompleted = true;
			} catch (InterruptedException | TimeoutException e) {
				log.log(Level.SEVERE, "Clean up was canceled due to an exception", e);
			} finally {
				lock.unlock();
			}
		}
	}

	/**
	 * Processes the incoming message.
	 * 
	 * @param message
	 * @throws GPIOException
	 */
	private void processIncomingMessage(Message message) throws GPIOInvalidPortNumException {
		switch (message.getMessageHeader().getMessageType()) {
		case GET_CONFIGURATION_RESPONSE:
			GetConfigurationResponse response = (GetConfigurationResponse) message;
			List<Configuration> configurations = response.getConfiguration();
			// for each configuration result
			for (Configuration conf : configurations
					.toArray(new Configuration[configurations.size()])) {
				// if pin configuration
				if (conf instanceof IOConfiguration) {
					IOConfiguration ioConf = (IOConfiguration) conf;
					boolean isRequestedGPIPort = false;
					boolean isRequestedGPOPort = false;
					switch (ioConf.getDirection()) {
					case INPUT:
						if (executingRequest.gpoPortNum == ioConf.getId()) {
							throw new GPIOInvalidPortNumException("Requested GPO port "
									+ executingRequest.gpoPortNum + " is a GPI port");
						}
						isRequestedGPIPort = executingRequest.gpiPortNum == 0
								|| executingRequest.gpiPortNum == ioConf.getId();
						break;
					case OUTPUT:
						if (executingRequest.gpiPortNum == ioConf.getId()) {
							throw new GPIOInvalidPortNumException("Requested GPI port "
									+ executingRequest.gpiPortNum + " is a GPO port");
						}
						isRequestedGPOPort = executingRequest.gpoPortNum == 0
								|| executingRequest.gpoPortNum == ioConf.getId();
					}
					// if the configuration for the pin is not requested
					if (!isRequestedGPIPort && !isRequestedGPOPort) {
						// remove the configuration from the result
						configurations.remove(conf);
					}
				}
			}
			// fall through
		case SET_CONFIGURATION_RESPONSE:
		case RESET_CONFIGURATION_RESPONSE:
			// deliver the response
			eventQueue.put(new GPIOMessageEvent(message));
			// remove the current executing request
			executingRequest = null;
			break;
		case STATE_CHANGED:
			// deliver the event
			eventQueue.put(new GPIOMessageEvent(message));
			break;
		case KEEP_ALIVE:
			// not implemented; it makes no sense for reflection or OSGi
			// communication
		case CONNECTION_ATTEMPTED:
			// this event is ignored because a LLRP client is connected
		default:
		}
	}

	/**
	 * Starts the execution of the next enqueued request if no request is
	 * executed currently.
	 * 
	 * @throws GPIOException
	 */
	private void processRequest() throws GPIOException {
		// if no request is in execution and requests are available
		if (executingRequest == null && requestQueue.size() > 0) {
			// get next request and process it
			executingRequest = requestQueue.remove(0);
			switch (executingRequest.type) {
			case GET_CONFIGURATION:
			case SET_CONFIGURATION:
			case RESET_CONFIGURATION:
				Message message = (Message) executingRequest.request;
				// add the request to the event queue (for info only)
				eventQueue.put(new GPIOMessageEvent(message));
				// send the request
				gpioClient.requestSendingData(gpioChannel, message);
				break;
			default:
			}
		}
	}
}
