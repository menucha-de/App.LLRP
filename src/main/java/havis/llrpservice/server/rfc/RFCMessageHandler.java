package havis.llrpservice.server.rfc;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.device.rf.RFDevice;
import havis.device.rf.capabilities.CapabilityType;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.result.OperationResult;
import havis.llrpservice.common.concurrent.EventPipe;
import havis.llrpservice.common.concurrent.EventPipes;
import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.ids.IdGenerator;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.GPIEvent;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.sbc.rfc.RFCClientMultiplexed;
import havis.llrpservice.sbc.rfc.RFCConnectionException;
import havis.llrpservice.sbc.rfc.RFCEventHandler;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.RFCUnknownChannelException;
import havis.llrpservice.sbc.rfc.event.RFCChannelClosedEvent;
import havis.llrpservice.sbc.rfc.event.RFCChannelOpenedEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataReceivedNotifyEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataSentEvent;
import havis.llrpservice.sbc.rfc.message.ExecuteResponse;
import havis.llrpservice.sbc.rfc.message.GetCapabilities;
import havis.llrpservice.sbc.rfc.message.GetConfiguration;
import havis.llrpservice.sbc.rfc.message.GetOperations;
import havis.llrpservice.sbc.rfc.message.Message;
import havis.llrpservice.sbc.rfc.message.MessageHeader;
import havis.llrpservice.sbc.rfc.message.MessageType;
import havis.llrpservice.sbc.rfc.message.ResetConfiguration;
import havis.llrpservice.sbc.rfc.message.SetConfiguration;
import havis.llrpservice.sbc.service.ReflectionServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutionPosition;
import havis.llrpservice.server.rfc.ROSpecExecutor.ROSpecExecutorListener;
import havis.llrpservice.server.rfc.messageData.ExecuteResponseData;
import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.ReflectionType;
import havis.llrpservice.xml.configuration.RfcPortProperties;
import havis.util.platform.Platform;

/**
 * The RFCMessageHandler handles the communication to a RF controller.
 * <p>
 * A connection to the RF controller is opened with
 * {@link #open(ExecutorService)} and closed with {@link #close()}. Before the
 * message handler can be opened again the method {@link #run} must have been
 * started and stopped in a separate thread.
 * </p>
 * <p>
 * Requests for the RF controller can be enqueued with methods like
 * {@link #requestExecution(ROSpec)} or {@link #requestCapabilities(List)}. The
 * sent and received RFC messages to/from the controller are put to an event
 * queue.
 * </p>
 */
public class RFCMessageHandler implements Runnable {

	private static final Logger log = Logger.getLogger(RFCMessageHandler.class.getName());

	// properties from configuration
	private final int openCloseTimeout;
	private final AddressGroup llrpAddress;

	private final RFCClientMultiplexed rfcClient;
	// the callback for the RFC client
	private final EventHandler rfcEventHandler = new EventHandler();
	// the opened channel to the RFC controller
	private SocketChannel rfcChannel;

	private final EventQueue eventQueue;

	private final List<RFCMessageHandlerListener> listeners = new CopyOnWriteArrayList<>();
	// AccessSpecId -> AccessSpec
	private final Map<Long, AccessSpec> accessSpecs = new HashMap<>();

	private final List<InternalRequest> requestQueue = new ArrayList<>();
	private InternalRequest executingRequest = null;

	private final Object callbackLock = new Object();
	private ROSpecExecutionPosition roSpecExecutionStopPosition;

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

		// EXECUTE
		ROSpecExecutor roSpecExecutor;

		InternalRequest(MessageType type, Object request) {
			this.type = type;
			this.request = request;
		}
	}

	/**
	 * The callback implementation for a RFC client.
	 */
	private class EventHandler implements RFCEventHandler {

		private EventPipes latches = new EventPipes(new ReentrantLock());

		public SocketChannel awaitOpening(long timeout)
				throws InterruptedException, TimeoutException {
			List<RFCChannelOpenedEvent> events = latches.await(RFCChannelOpenedEvent.class,
					timeout);
			return events.size() == 0 ? null : events.get(0).getChannel();
		}

		public RFCException awaitClosing(long timeout)
				throws InterruptedException, TimeoutException {
			List<RFCChannelClosedEvent> events = latches.await(RFCChannelClosedEvent.class,
					timeout);
			return events.size() == 0 ? null : events.get(0).getException();
		}

		@Override
		public void channelOpened(RFCChannelOpenedEvent event) {
			latches.fire(event);
		}

		@Override
		public void dataSent(RFCDataSentEvent event) {
			// RFCClientMultiplexed does not sent this event with an exception
			// channelClosed is used instead
		}

		@Override
		public void dataReceived(RFCDataReceivedNotifyEvent event) {
		}

		@Override
		public void channelClosed(RFCChannelClosedEvent event) {
			latches.fire(event);
		}

	}

	/**
	 * @param serverConfiguration
	 * @param instanceConfiguration
	 * @param eventQueue
	 *            event queue for sent and received RFC messages (enqueued as
	 *            {@link RFCMessageEvent} or {@link InternalRFCMessageEvent})
	 * @param serviceFactory
	 *            The service factory for RF controllers provided by the OSGi
	 *            platform. If it is <code>null</code> then the LLRP
	 *            configuration must provide the properties for creating a RF
	 *            controller via the Java Reflection API.
	 * @throws EntityManagerException
	 * @throws ConfigurationException
	 * @throws PersistenceException
	 * @throws ClassNotFoundException
	 *             the creating of a RF controller via Java Reflection API
	 *             failed
	 * @throws MissingServiceFactoryException
	 */
	public RFCMessageHandler(ServerConfiguration serverConfiguration,
			ServerInstanceConfiguration instanceConfiguration, EventQueue eventQueue,
			ServiceFactory<RFDevice> serviceFactory, Platform platform)
			throws EntityManagerException, ConfigurationException, PersistenceException,
			ClassNotFoundException, MissingServiceFactoryException {
		// create config analyser
		Entity<LLRPServerConfigurationType> serverConfigEntity = serverConfiguration.acquire();
		Entity<LLRPServerInstanceConfigurationType> instanceConfigEntity = instanceConfiguration
				.acquire();
		RFCConfigAnalyser configAnalyser = new RFCConfigAnalyser(serverConfigEntity.getObject());
		configAnalyser.setServerInstanceConfig(instanceConfigEntity.getObject());
		instanceConfiguration.release(instanceConfigEntity, false /* write */);
		serverConfiguration.release(serverConfigEntity, false /* write */);

		llrpAddress = configAnalyser.getAddress();
		RfcPortProperties rfcPortProperties = configAnalyser.getRFCPortProperties();
		openCloseTimeout = rfcPortProperties.getOpenCloseTimeout();

		if (rfcPortProperties.ifReflection()) {
			ReflectionType reflectionProps = rfcPortProperties.getReflection();
			String addressSetterMethodName = reflectionProps.getAddressSetterMethodName();
			if (addressSetterMethodName != null) {
				addressSetterMethodName = addressSetterMethodName.trim();
			}
			serviceFactory = new ReflectionServiceFactory<>(
					reflectionProps.getControllerClassName().trim(), addressSetterMethodName);
		} else if (rfcPortProperties.ifOSGi() && serviceFactory == null) {
			throw new MissingServiceFactoryException("Missing OSGi service factory");
		}
		// create a RFC client using an own instance for the RFC interface
		rfcClient = new RFCClientMultiplexed(serviceFactory, openCloseTimeout,
				rfcPortProperties.getCallbackTimeout(), platform);

		this.eventQueue = eventQueue;
	}

	/**
	 * Adds a listener.
	 * 
	 * @param listener
	 */
	public void addListener(RFCMessageHandlerListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(RFCMessageHandlerListener listener) {
		List<RFCMessageHandlerListener> removed = new ArrayList<>();
		for (RFCMessageHandlerListener entry : listeners) {
			if (listener == entry) {
				removed.add(entry);
			}
		}
		listeners.removeAll(removed);
	}

	/**
	 * Opens the message handler synchronously. If it is opened and
	 * {@link #run()} has been started a
	 * {@link RFCMessageHandlerListener#opened()} event is sent to registered
	 * listeners.
	 * 
	 * @param threadPool
	 * @throws RFCException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public void open(ExecutorService threadPool)
			throws RFCException, InterruptedException, TimeoutException {
		thread = threadPool.submit(this);
		lock.lock();
		try {
			hasThreadCompleted = false;
			// request the opening of the channel
			rfcClient.requestOpeningChannel(llrpAddress.getHost(), llrpAddress.getPort(),
					rfcEventHandler);
			// wait for opened channel
			try {
				rfcChannel = rfcEventHandler.awaitOpening(openCloseTimeout);
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
	 * Closes the message handler synchronously. The connection to the RF
	 * controller is canceled and method {@link #run} is stopped. Before
	 * {@link #run} stops a
	 * {@link RFCMessageHandlerListener#closed(List, Throwable)} event is sent
	 * to registered listeners.
	 * 
	 * @throws RFCConnectionException
	 * @throws RFCException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 *             time out for closing the channel to the client
	 * @throws ExecutionException
	 */
	public void close() throws RFCConnectionException, RFCException, InterruptedException,
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
				// event if opening failed)
				runLatch.fire(RunLatchEvent.CLOSED);
			}
			if (rfcChannel != null) {
				// close the channel to the client (a blocked "run" method
				// is released)
				rfcClient.requestClosingChannel(rfcChannel);
				try {
					RFCException exception = rfcEventHandler.awaitClosing(openCloseTimeout);
					if (exception != null) {
						throw exception;
					}
				} catch (TimeoutException e) {
					throw new TimeoutException(
							"Unable to close the channel within " + openCloseTimeout + " ms");
				}
				rfcChannel = null;
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
	 * Requests capabilities.
	 * <p>
	 * The request is sent to the event queue directly before it will be
	 * processed. The response is sent to the event queue after the message has
	 * been received.
	 * </p>
	 * 
	 * @param capTypes
	 * @return message identifier
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 */
	public long requestCapabilities(List<CapabilityType> capTypes)
			throws RFCException, UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException {
		lock.lock();
		try {
			// enqueue a request
			long id = IdGenerator.getNextLongId();
			requestQueue.add(new InternalRequest(MessageType.GET_CAPABILITIES,
					new GetCapabilities(new MessageHeader(id), capTypes)));
			// if no request is executed then start the next
			// available request
			processRequest();
			return id;
		} finally {
			lock.unlock();
		}
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
	 * @param antennaID
	 * @return message identifier
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 */
	public long requestConfiguration(List<ConfigurationType> confTypes, short antennaID)
			throws RFCException, UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException {
		lock.lock();
		try {
			// enqueue a request
			long id = IdGenerator.getNextLongId();
			requestQueue.add(new InternalRequest(MessageType.GET_CONFIGURATION,
					new GetConfiguration(new MessageHeader(id), confTypes, antennaID)));
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
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 */
	public long requestExecution(boolean reset, List<Configuration> configurations)
			throws RFCException, UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException {
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
	 * Requests the execution of an ROSpec.
	 * <p>
	 * The requests are sent to the event queue directly before they will be
	 * processed. The responses are sent to the event queue after the messages
	 * have been received.
	 * </p>
	 * 
	 * @param roSpec
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 */
	public void requestExecution(ROSpec roSpec) throws RFCException, UnsupportedSpecTypeException,
			UnsupportedAccessOperationException, UnsupportedAirProtocolException {
		lock.lock();
		try {
			// create a request
			InternalRequest request = new InternalRequest(MessageType.EXECUTE, roSpec);
			// create the executor and add existing accessSpecs
			// (the list of accessSpec can be modified at any time with
			// add(AccessSpec) or remove(AccessSpec) method)
			request.roSpecExecutor = new ROSpecExecutor(roSpec, rfcClient, rfcChannel, eventQueue);
			request.roSpecExecutor.addListener(new ROSpecExecutorListener() {

				@Override
				public void isStopped(ROSpec roSpec, ROSpecExecutionPosition pos) {
					synchronized (callbackLock) {
						roSpecExecutionStopPosition = pos;
					}
				}
			});
			for (AccessSpec accessSpec : accessSpecs.values()) {
				request.roSpecExecutor.add(accessSpec);
			}
			// add a listener for changes of the AccessSpec list and forward the
			// events to the own listeners
			request.roSpecExecutor.addListener(new AccessSpecsListener() {

				@Override
				public void removed(long accessSpecId) {
					// remove AccessSpec from local list
					lock.lock();
					try {
						accessSpecs.remove(accessSpecId);
					} finally {
						lock.unlock();
					}
					// fire "removedAccessSpec" events
					for (RFCMessageHandlerListener listener : listeners) {
						listener.removedAccessSpec(accessSpecId);
					}
				}
			});
			// enqueue the request
			requestQueue.add(request);
			// if no request is executed then start the next
			// available request
			processRequest();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Cancels the execution of a ROSpec.
	 * 
	 * @param roSpecId
	 */
	public void cancelExecution(long roSpecId) {
		lock.lock();
		try {
			// if ROSpec is executed currently
			if (executingRequest != null && executingRequest.type.equals(MessageType.EXECUTE)
					&& ((ROSpec) executingRequest.request).getRoSpecID() == roSpecId) {
				// stop the execution by setting the current execution position;
				// the roSpecExecutor must not be stopped here because the
				// running execution would be stopped rigorously;
				// the last execute response informs the caller about the end of
				// the execution
				ROSpecExecutionPosition pos = executingRequest.roSpecExecutor
						.getExecutionPosition();
				synchronized (callbackLock) {
					roSpecExecutionStopPosition = pos;
				}
			} else {
				// remove pending request
				int index = -1;
				for (int i = 0; i < requestQueue.size(); i++) {
					InternalRequest request = requestQueue.get(i);
					if (request.type.equals(MessageType.EXECUTE)
							&& ((ROSpec) request.request).getRoSpecID() == roSpecId) {
						index = i;
						break;
					}
				}
				if (index >= 0) {
					requestQueue.remove(index);
				}
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Adds an AccessSpec. The AccessSpec must be enabled.
	 * 
	 * @param accessSpec
	 * @throws UnsupportedAirProtocolException
	 */
	public void add(AccessSpec accessSpec) throws UnsupportedAirProtocolException {
		lock.lock();
		try {
			// add AccessSpec to local list
			accessSpecs.put(accessSpec.getAccessSpecId(), accessSpec);
			// add AccessSpec to existing requests
			if (executingRequest != null && executingRequest.type.equals(MessageType.EXECUTE)) {
				executingRequest.roSpecExecutor.add(accessSpec);
			}
			for (InternalRequest request : requestQueue) {
				if (request.type.equals(MessageType.EXECUTE)) {
					request.roSpecExecutor.add(accessSpec);
				}
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Removes an AccessSpec.
	 * 
	 * @param accessSpecId
	 * @return The removed AccessSpec
	 */
	public AccessSpec remove(long accessSpecId) {
		lock.lock();
		try {
			// remove AccessSpec from local list
			AccessSpec accessSpec = accessSpecs.remove(accessSpecId);
			// remove AccessSpec from existing requests
			if (executingRequest != null && executingRequest.type.equals(MessageType.EXECUTE)) {
				executingRequest.roSpecExecutor.remove(accessSpecId);
			}
			for (InternalRequest request : requestQueue) {
				if (request.type.equals(MessageType.EXECUTE)) {
					request.roSpecExecutor.remove(accessSpecId);
				}
			}
			return accessSpec;
		} finally {
			lock.unlock();
		}
	}

	public void gpiEventReceived(GPIEvent event) {
		lock.lock();
		try {
			// if nothing is processed
			if (executingRequest == null) {
				return;
			}
			executingRequest.roSpecExecutor.gpiEventReceived(event);
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
			for (RFCMessageHandlerListener listener : listeners) {
				listener.opened();
			}
			while (true) {
				// wait for next message
				Message message = rfcClient.awaitReceivedData(rfcChannel,
						RFCClientMultiplexed.NO_TIMEOUT);
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
		} catch (RFCUnknownChannelException e) {
			lock.lock();
			try {
				// if the channel has been closed from RFCClientMultiplexed
				if (rfcChannel != null) {
					// if the channel has been closed a close event must have
					// been sent -> forward the exception of the close event
					try {
						throwable = rfcEventHandler.awaitClosing(openCloseTimeout);
					} catch (Exception ex) {
						log.log(Level.SEVERE, "Cannot get the cause for the closed channel", e);
					}
					rfcChannel = null;
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
				for (RFCMessageHandlerListener listener : listeners) {
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
	 * @throws MissingTagDataException
	 * @throws UnsupportedAccessOperationException
	 * @throws RFCException
	 */
	private void processIncomingMessage(Message message)
			throws MissingTagDataException, UnsupportedAccessOperationException, RFCException {
		switch (message.getMessageHeader().getMessageType()) {
		case GET_CAPABILITIES_RESPONSE:
		case GET_CONFIGURATION_RESPONSE:
		case SET_CONFIGURATION_RESPONSE:
		case RESET_CONFIGURATION_RESPONSE:
			// deliver the response
			eventQueue.put(new RFCMessageEvent(message));
			// remove the current executing request
			executingRequest = null;
			break;
		case GET_OPERATIONS:
			eventQueue.put(new RFCMessageEvent(message));
			// execute the requested access operations directly without adding
			// it to the request queue
			// (it has the highest priority because an Execute request is
			// currently being processed;
			// a GetOperationsResponse message is added for info purposes to
			// the event queue by AccessSpecExecutor)
			executingRequest.roSpecExecutor.startNextAccessOps((GetOperations) message);
			break;
		case EXECUTE_RESPONSE:
			ExecuteResponse exResponse = (ExecuteResponse) message;
			// remove internal access operation results from
			// response
			removeInternalAccessOpResults(exResponse);
			// inform ROSpec executor about the response
			executingRequest.roSpecExecutor.executionResponseReceived(exResponse.getTagData());
			// create RFC message event incl. LLRP position data
			ROSpecExecutionPosition pos;
			synchronized (callbackLock) {
				pos = roSpecExecutionStopPosition;
			}
			boolean isLastResponse = pos != null;
			if (!isLastResponse) {
				// get current position
				pos = executingRequest.roSpecExecutor.getExecutionPosition();
			}
			eventQueue.put(new RFCMessageEvent(exResponse,
					new ExecuteResponseData(pos.getRoSpecId(), pos.getSpecIndex(),
							pos.getInventoryParameterSpecId(), pos.getAntennaId(),
							pos.getProtocolId(), getTagDataAccessSpecIds(pos, exResponse),
							isLastResponse)));
			if (isLastResponse) {
				// if the execution shall be cancelled ("cancelExecution" has
				// been called) then the roSpecExecutor must be stopped
				executingRequest.roSpecExecutor.stop();
				// the stop position must be reset here because
				// roSpecExecutor.stop fires an isStopped event
				synchronized (callbackLock) {
					roSpecExecutionStopPosition = null;
				}
			} else {
				// enqueue a request for the next inventory
				// (existing requests have higher priority and must be executed
				// first)
				requestQueue.add(executingRequest);
			}
			executingRequest = null;
			break;
		case KEEP_ALIVE:
			// not implemented; it makes no sense for reflection
			// or OSGi communication
		case CONNECTION_ATTEMPTED:
			// this event is ignored because a LLRP client is connected
		case EXECUTE:
		case GET_CAPABILITIES:
		case GET_CONFIGURATION:
		case GET_OPERATIONS_RESPONSE:
		case SET_CONFIGURATION:
		case RESET_CONFIGURATION:
			// outgoing messages are not processed here
		default:
			break;
		}
	}

	/**
	 * Starts the execution of the next enqueued request if no request is
	 * executed currently.
	 * 
	 * @throws RFCException
	 * @throws UnsupportedSpecTypeException
	 * @throws UnsupportedAccessOperationException
	 * @throws UnsupportedAirProtocolException
	 */
	private void processRequest() throws RFCException, UnsupportedSpecTypeException,
			UnsupportedAccessOperationException, UnsupportedAirProtocolException {
		// if no request is in execution and requests are available
		if (executingRequest == null && requestQueue.size() > 0) {
			// get next request and process it
			executingRequest = requestQueue.remove(0);
			switch (executingRequest.type) {
			case GET_CONFIGURATION:
			case GET_CAPABILITIES:
			case SET_CONFIGURATION:
			case RESET_CONFIGURATION:
				Message msg = (Message) executingRequest.request;
				// add the request to the event queue (for info only)
				eventQueue.put(new RFCMessageEvent(msg));
				// send the request
				rfcClient.requestSendingData(rfcChannel, msg);
				break;
			case EXECUTE:
				// execute the next inventory (an Execute request is added for
				// info purposes to the event queue by AISpecExecutor)
				executingRequest.roSpecExecutor.startNextAction();
				break;
			case GET_OPERATIONS_RESPONSE:
				// outgoing message which is not enqueued but is set directly to
				// the channel data due to synchronous call
			case EXECUTE_RESPONSE:
			case GET_CAPABILITIES_RESPONSE:
			case GET_CONFIGURATION_RESPONSE:
			case GET_OPERATIONS:
			case SET_CONFIGURATION_RESPONSE:
			case RESET_CONFIGURATION_RESPONSE:
			case KEEP_ALIVE:
			case CONNECTION_ATTEMPTED:
				// incoming messages are not processed here
			default:
				break;
			}
		}
	}

	private void removeInternalAccessOpResults(ExecuteResponse response) {
		for (TagData data : response.getTagData()) {
			List<OperationResult> remove = new ArrayList<>();
			List<OperationResult> results = data.getResultList();
			for (OperationResult result : results) {
				// if the operation result has a generated id
				if (result.getOperationId().startsWith(AccessSpecExecutor.GENERATED_ID_PREFIX)) {
					remove.add(result);
				}
			}
			for (OperationResult r : remove) {
				results.remove(r);
			}
		}
	}

	/**
	 * Gets the AccessSpecIds for an execute response.
	 * 
	 * @param pos
	 * @param response
	 * @return map of {@link TagData#getTagDataId()} to AccessSpecId
	 */
	private Map<Long, Long> getTagDataAccessSpecIds(ROSpecExecutionPosition pos,
			ExecuteResponse response) {
		Map<Long, Long> ret = new HashMap<>(pos.getTagDataAccessSpecIds());
		Long defaultAccessSpecId = pos.getDefaultAccessSpecId();
		if (defaultAccessSpecId != null) {
			for (TagData data : response.getTagData()) {
				List<OperationResult> results = data.getResultList();
				if (results != null && !results.isEmpty()) {
					ret.put(data.getTagDataId(), defaultAccessSpecId);
				}
			}
		}
		return ret;
	}
}
