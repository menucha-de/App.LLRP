package havis.llrpservice.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.device.io.IODevice;
import havis.device.rf.RFDevice;
import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.fsm.FSM;
import havis.llrpservice.common.fsm.FSMActionException;
import havis.llrpservice.common.fsm.FSMGuardException;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.common.tcp.TCPConnectorStoppedException;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.common.tcp.TCPUnknownChannelException;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.AddAccessSpec;
import havis.llrpservice.data.message.AddROSpec;
import havis.llrpservice.data.message.CustomMessage;
import havis.llrpservice.data.message.DeleteAccessSpec;
import havis.llrpservice.data.message.DeleteROSpec;
import havis.llrpservice.data.message.DisableAccessSpec;
import havis.llrpservice.data.message.DisableROSpec;
import havis.llrpservice.data.message.EnableAccessSpec;
import havis.llrpservice.data.message.EnableROSpec;
import havis.llrpservice.data.message.GetAccessSpecs;
import havis.llrpservice.data.message.GetROSpecs;
import havis.llrpservice.data.message.GetSupportedVersion;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageTypes.MessageType;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.SetProtocolVersion;
import havis.llrpservice.data.message.StartROSpec;
import havis.llrpservice.data.message.StopROSpec;
import havis.llrpservice.data.message.parameter.AccessSpec;
import havis.llrpservice.data.message.parameter.EventNotificationState;
import havis.llrpservice.data.message.parameter.EventNotificationStateEventType;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.ROReportSpec;
import havis.llrpservice.data.message.parameter.ROSpec;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;
import havis.llrpservice.data.message.parameter.ROSpecEvent;
import havis.llrpservice.data.message.parameter.ReaderExceptionEvent;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.sbc.gpio.GPIOException;
import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.Event;
import havis.llrpservice.server.event.EventPriority;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.GPIOMessageEvent;
import havis.llrpservice.server.event.LLRPMessageEvent;
import havis.llrpservice.server.event.LLRPParameterEvent;
import havis.llrpservice.server.event.LLRPServiceInstanceEvent;
import havis.llrpservice.server.event.RFCMessageEvent;
import havis.llrpservice.server.gpio.GPIOMessageHandler;
import havis.llrpservice.server.gpio.GPIOMessageHandlerListener;
import havis.llrpservice.server.llrp.LLRPConfigAnalyser;
import havis.llrpservice.server.llrp.LLRPMessageHandler;
import havis.llrpservice.server.llrp.LLRPMessageHandlerListener;
import havis.llrpservice.server.platform.PlatformManager;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.rfc.RFCMessageHandlerListener;
import havis.llrpservice.server.rfc.UnsupportedAccessOperationException;
import havis.llrpservice.server.rfc.UnsupportedAirProtocolException;
import havis.llrpservice.server.rfc.UnsupportedSpecTypeException;
import havis.llrpservice.server.rfc.messageData.ROReportSpecData;
import havis.llrpservice.server.service.ROReportSpecsManager.ROReportSpecsManagerListener;
import havis.llrpservice.server.service.ROSpecsManager.ROSpecsManagerListener;
import havis.llrpservice.server.service.data.ROAccessReportEntity;
import havis.llrpservice.server.service.fsm.FSMCreator;
import havis.llrpservice.server.service.fsm.FSMEvent;
import havis.llrpservice.server.service.fsm.FSMEvents;
import havis.llrpservice.server.service.fsm.LLRPServiceInstanceRuntimeData;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;
import havis.llrpservice.server.service.messageHandling.LLRPMessageCreator;
import havis.llrpservice.server.service.messageHandling.LLRPMessageValidator;
import havis.llrpservice.server.service.messageHandling.ROAccessReportCreator;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.properties.DefaultsGroup;
import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

/**
 * Class to collect, process and distribute messages.
 * 
 */
public class LLRPServiceInstance implements Runnable {
	private static final Logger log = Logger.getLogger(LLRPServiceInstance.class.getName());

	private static final int DELAY_PER_RETRY = 1000;

	public interface LLRPServiceInstanceListener {
		/**
		 * The service instance has been opened.
		 * 
		 * @param llrpPort
		 */
		public void opened(int llrpPort);

		/**
		 * The service instance has been closed.
		 * 
		 * @param t
		 *            <code>null</code> if the service instance has been closed
		 *            cleanly
		 * @param isRestarting
		 *            <code>true</code> if the service instance restarts.
		 */
		public void closed(Throwable t, boolean isRestarting);
	}

	private class RFCMessageHandlerInstanceListener implements RFCMessageHandlerListener {
		@Override
		public void opened() {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.RFC_CLIENT_OPENED, null /* exception */));
		}

		@Override
		public void closed(List<Object> pendingRequests, Throwable t) {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.RFC_CLIENT_CLOSED, t));
		}

		@Override
		public void removedAccessSpec(long accessSpecId) {
		}
	}

	private class GPIOMessageHandlerInstanceListener implements GPIOMessageHandlerListener {
		@Override
		public void opened() {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.GPIO_CLIENT_OPENED, null /* exception */));
		}

		@Override
		public void closed(List<Object> pendingRequests, Throwable t) {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.GPIO_CLIENT_CLOSED, t));
		}
	}

	/**
	 * Nested class to handle LLRPMessageHandler events
	 * 
	 */
	private class LLRPMessageHandlerInstanceListener implements LLRPMessageHandlerListener {
		@Override
		public void dataSent(LLRPDataSentEvent event) {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.LLRP_DATA_SENT, event.getException()),
					EventPriority.LLRP);
		}

		@Override
		public void opened(LLRPChannelOpenedEvent event) {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.LLRP_CLIENT_OPENED, null /* exception */),
					EventPriority.LLRP);
		}

		@Override
		public void closed(LLRPChannelClosedEvent event) {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.LLRP_CLIENT_CLOSED, event.getException()),
					EventPriority.LLRP);
		}

		@Override
		public void opened() {
			eventQueue.put(new LLRPServiceInstanceEvent(
					LLRPServiceInstanceEvent.MessageType.LLRP_SERVER_OPENED, null /* exception */),
					EventPriority.LLRP);
		}

		@Override
		public void closed(Throwable t) {
			eventQueue.put(
					new LLRPServiceInstanceEvent(
							LLRPServiceInstanceEvent.MessageType.LLRP_SERVER_CLOSED, t),
					EventPriority.LLRP);
		}
	}

	private final ServerConfiguration serverConfiguration;
	private final XMLFile<LLRPServerInstanceConfigurationType> instanceConfigurationFile;
	private final TCPServerMultiplexed tcpServerLLRP;
	private final DefaultsGroup instancesProperties;
	private final int unexpectedTimeout;
	private final ServiceFactory<Platform> platformServiceFactory;
	private final ServiceFactory<RFDevice> rfcServiceFactory;
	private final ServiceFactory<IODevice> gpioServiceFactory;
	private EventQueue eventQueue = new EventQueue();
	private Semaphore isCanceled = new Semaphore(0);
	private List<LLRPServiceInstanceListener> listeners = new CopyOnWriteArrayList<>();

	/**
	 * @param configuration
	 * @param instanceConfigurationFile
	 * @param instancesProperties
	 * @param unexpectedTimeout
	 *            time out in seconds
	 * @param tcpServerLLRP
	 * @param platformServiceFactory
	 *            The service factory for platform controllers provided by the
	 *            OSGi platform. If it is <code>null</code> then the LLRP
	 *            configuration must provide the properties for creating a
	 *            platform controller via the Java Reflection API.
	 * @param rfcServiceFactory
	 *            The service factory for RF controllers provided by the OSGi
	 *            platform. If it is <code>null</code> then the LLRP
	 *            configuration must provide the properties for creating a RF
	 *            controller via the Java Reflection API.
	 * @param gpioServiceFactory
	 *            The service factory for GPIO controllers provided by the OSGi
	 *            platform. If it is <code>null</code> then the LLRP
	 *            configuration must provide the properties for creating a GPIO
	 *            controller via the Java Reflection API.
	 */
	public LLRPServiceInstance(ServerConfiguration configuration,
			XMLFile<LLRPServerInstanceConfigurationType> instanceConfigurationFile,
			DefaultsGroup instancesProperties, int unexpectedTimeout,
			TCPServerMultiplexed tcpServerLLRP, ServiceFactory<Platform> platformServiceFactory,
			ServiceFactory<RFDevice> rfcServiceFactory,
			ServiceFactory<IODevice> gpioServiceFactory) {
		this.serverConfiguration = configuration;
		this.instanceConfigurationFile = instanceConfigurationFile;
		this.instancesProperties = instancesProperties;
		this.unexpectedTimeout = unexpectedTimeout;
		this.tcpServerLLRP = tcpServerLLRP;
		this.platformServiceFactory = platformServiceFactory;
		this.rfcServiceFactory = rfcServiceFactory;
		this.gpioServiceFactory = gpioServiceFactory;
	}

	/**
	 * Adds a listener.
	 * 
	 * @param listener
	 */
	public void addListener(LLRPServiceInstanceListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(LLRPServiceInstanceListener listener) {
		List<LLRPServiceInstanceListener> removed = new ArrayList<>();
		for (LLRPServiceInstanceListener entry : listeners) {
			if (listener == entry) {
				removed.add(entry);
			}
		}
		listeners.removeAll(removed);
	}

	private void fireOpenEvents(String instanceId, int llrpPort, boolean isRestarted) {
		if (listeners.isEmpty()) {
			if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO, (isRestarted ? "Restarted" : "Started") + " instance "
						+ instanceId + " on port " + llrpPort);
			}
		} else {
			for (LLRPServiceInstanceListener listener : listeners) {
				listener.opened(llrpPort);
			}
		}
	}

	private void fireCloseEvents(String instanceId, int llrpPort, Throwable t,
			boolean isRestarting) {
		if (listeners.isEmpty()) {
			if (t != null) {
				log.log(Level.SEVERE, "Stopped instance '" + instanceId + "'"
						+ (isRestarting ? " for restart" : "") + " with exception", t);
			} else if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO,
						"Stopped instance " + instanceId + (isRestarting ? " for restart" : ""));
			}
		} else {
			for (LLRPServiceInstanceListener listener : listeners) {
				listener.closed(t, isRestarting);
			}
		}
	}

	/**
	 * Cancels the execution of the instance (method {@link #run()}).
	 */
	public void cancelExecution() {
		// set a cancellation flag to avoid the starting of the instance if it
		// is being stopped currently
		isCanceled.release();
		// cancel the waiting for events
		eventQueue.put(new LLRPServiceInstanceEvent(LLRPServiceInstanceEvent.MessageType.CANCEL,
				null /* exception */), EventPriority.SERVICE_INSTANCE);
	}

	/**
	 * Runs the instance.
	 */
	public void run() {
		isCanceled.drainPermits();

		ServerInstanceConfiguration instanceConfiguration;
		String instanceId;
		int llrpPort;

		Platform platform;
		PlatformManager platformManager;
		ROSpecsManager roSpecsManager;
		AccessSpecsManager accessSpecsManager;
		ROAccessReportDepot reportDepot;
		LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData;
		ExecutorService threadPool = Executors.newFixedThreadPool(3);
		LLRPMessageHandler llrpMessageHandler;
		LLRPRuntimeData llrpRuntimeData = null;
		RFCMessageHandler rfcMessageHandler;
		GPIOMessageHandler gpioMessageHandler = null;

		FSMEvents fsmEvents = null;
		FSM<FSMEvent> fsm = null;

		try {
			// get copy of server configuration
			Entity<LLRPServerConfigurationType> serverConfigEntity = serverConfiguration.acquire();
			LLRPServerConfigurationType serverConf = serverConfigEntity.getObject();
			serverConfiguration.release(serverConfigEntity, false /* write */);
			// get instance properties from config
			LLRPServerInstanceConfigurationType instanceConf = instanceConfigurationFile
					.getContent();
			InstanceConfigAnalyser configAnalyser = new InstanceConfigAnalyser(serverConf,
					instanceConf);
			instanceId = configAnalyser.getInstanceId();
			boolean hasGPIO = configAnalyser.hasGPIO();
			// get LLRP properties from config
			LLRPConfigAnalyser llrpConfigAnalyser = new LLRPConfigAnalyser(serverConf);
			llrpConfigAnalyser.setServerInstanceConfig(instanceConf);
			llrpPort = llrpConfigAnalyser.getAddress().getPort();
			// open instance configuration
			instanceConfiguration = new ServerInstanceConfiguration(serverConfiguration,
					instanceConfigurationFile);
			instanceConfiguration.open();
			// open a platform controller
			platformManager = new PlatformManager(serverConfiguration, instanceConfiguration,
					platformServiceFactory);
			platform = platformManager.getService();
			platform.open();
			// start the LLRP service
			llrpMessageHandler = new LLRPMessageHandler(serverConfiguration, instanceConfiguration,
					eventQueue, tcpServerLLRP);
			llrpMessageHandler.addListener(new LLRPMessageHandlerInstanceListener());
			llrpMessageHandler.open(platform, threadPool);
			// fire open events to instance listeners
			fireOpenEvents(instanceId, llrpPort, false /* isRestarting */);
			// create a RFC message handler
			// it is started/stopped when a LLRP client connects/disconnects
			rfcMessageHandler = new RFCMessageHandler(serverConfiguration, instanceConfiguration,
					eventQueue, rfcServiceFactory, platform);
			rfcMessageHandler.addListener(new RFCMessageHandlerInstanceListener());
			RFCRuntimeData rfcRuntimeData = new RFCRuntimeData(rfcMessageHandler);
			// open a ROAccessReport depot using the persistence of the instance
			// configuration
			reportDepot = new ROAccessReportDepot();
			reportDepot.open(instanceConfiguration.getPersistence());
			// create ROSpecs managers
			roSpecsManager = new ROSpecsManager(rfcMessageHandler, platform.hasUTCClock());
			// listen to changes of ROSpec executions
			roSpecsManager.addListener(new ROSpecsManagerListener() {

				@Override
				public void executionChanged(ROSpecEvent event) {
					// enqueue ROSpec event
					eventQueue.put(new LLRPParameterEvent(event));
				}
			});
			// listen to ROReportSpec triggers
			roSpecsManager.getROReportSpecsManager()
					.addListener(new ROReportSpecsManagerListener() {

						@Override
						public void report(long roSpecId, ROReportSpec roReportSpec) {
							// enqueue ROReportSpec event
							eventQueue.put(new LLRPParameterEvent(roReportSpec,
									new ROReportSpecData(roSpecId)));
						}
					});
			// create AccessSpecs manager
			accessSpecsManager = new AccessSpecsManager(rfcMessageHandler);
			llrpRuntimeData = new LLRPRuntimeData(instancesProperties.getIdentificationSource(),
					instancesProperties.getLLRPCapabilities(), llrpMessageHandler, roSpecsManager,
					reportDepot);
			GPIORuntimeData gpioRuntimeData = null;
			// if a GPIO config exists
			if (hasGPIO) {
				// create a GPIO message handler
				// it is started/stopped when a LLRP client connects/disconnects
				gpioMessageHandler = new GPIOMessageHandler(serverConfiguration,
						instanceConfiguration, eventQueue, gpioServiceFactory);
				gpioMessageHandler.addListener(new GPIOMessageHandlerInstanceListener());
				gpioRuntimeData = new GPIORuntimeData(gpioMessageHandler);
			}
			// create common runtime data
			llrpServiceInstanceRuntimeData = new LLRPServiceInstanceRuntimeData(platform,
					unexpectedTimeout);
			// create FSM
			fsmEvents = new FSMEvents(llrpServiceInstanceRuntimeData, llrpRuntimeData,
					rfcRuntimeData, gpioRuntimeData);
			fsm = new FSMCreator().create(fsmEvents);
		} catch (Throwable t) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, "Cannot start LLRP service instance", t);
			}
			return;
		}
		// create clean up handler
		final InstanceCleanup cleanup = new InstanceCleanup(roSpecsManager, accessSpecsManager,
				reportDepot, eventQueue);
		int retries = 0;
		Throwable exception = null;
		loop: while (true) {
			try {
				// Restart LLRP message handler (server) if necessary
				if (llrpRuntimeData.isRestartServer()) {
					int delay = 0;
					if (exception != null) {
						delay = DELAY_PER_RETRY * retries;
						log.log(Level.SEVERE,
								"The LLRP service instance will be restarted due to an exception (retry="
										+ retries + "/" + instancesProperties.getMaxStartupRetries()
										+ ", delay=" + delay + "ms)",
								exception);
					}
					// stop LLRP incl. dependencies
					stopLLRP(llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, cleanup);
					// if the execution is canceled while stopping LLRP
					// (the cancel event may have been removed from the event
					// queue by the clean up)
					if (isCanceled.tryAcquire()) {
						break loop;
					}
					// fire close events
					fireCloseEvents(instanceId, llrpPort, exception, true /* isRestarting */);
					exception = null;

					if (delay > 0) {
						Thread.sleep(delay);
					}

					// create new FSM
					llrpServiceInstanceRuntimeData = new LLRPServiceInstanceRuntimeData(platform,
							unexpectedTimeout);
					llrpRuntimeData = new LLRPRuntimeData(
							instancesProperties.getIdentificationSource(),
							instancesProperties.getLLRPCapabilities(), llrpMessageHandler,
							roSpecsManager, reportDepot);
					RFCRuntimeData rfcRuntimeData = new RFCRuntimeData(rfcMessageHandler);
					GPIORuntimeData gpioRuntimeData = null;
					// if GPIO is enabled
					if (gpioMessageHandler != null) {
						gpioRuntimeData = new GPIORuntimeData(gpioMessageHandler);
					}
					fsmEvents = new FSMEvents(llrpServiceInstanceRuntimeData, llrpRuntimeData,
							rfcRuntimeData, gpioRuntimeData);
					fsm = new FSMCreator().create(fsmEvents);
					// start the LLRP message handler
					llrpMessageHandler.open(llrpServiceInstanceRuntimeData.getPlatform(),
							threadPool);
					// fire open events
					fireOpenEvents(instanceId, llrpPort, true /* isRestarted */);
				}

				// Wait for new events in the event queue
				Event event = eventQueue.take(EventQueue.NO_TIMEOUT);

				// process event
				switch (event.getEventType()) {
				case LLRP_MESSAGE:
					processLLRPMessage((LLRPMessageEvent) event, fsm, fsmEvents, llrpRuntimeData,
							llrpServiceInstanceRuntimeData, accessSpecsManager);
					break;
				case LLRP_PARAMETER:
					processLLRPParameter((LLRPParameterEvent) event, llrpRuntimeData,
							llrpServiceInstanceRuntimeData);
					break;
				case RFC_MESSAGE:
					processRFCMessage((RFCMessageEvent) event, fsm, fsmEvents);
					break;
				case GPIO_MESSAGE:
					processGPIOMessage((GPIOMessageEvent) event, fsm, fsmEvents);
					break;
				case INSTANCE_EVENT:
					LLRPServiceInstanceEvent instanceEvent = (LLRPServiceInstanceEvent) event;
					if (instanceEvent.getException() != null) {
						throw instanceEvent.getException();
					}
					switch (instanceEvent.getMessageType()) {
					case LLRP_SERVER_OPENED:
					case LLRP_SERVER_CLOSED:
						break;
					case LLRP_CLIENT_OPENED:
						retries = 0;
						// open the RFC message handler
						rfcMessageHandler.open(threadPool);
						// if GPIO is enabled
						if (gpioMessageHandler != null) {
							// open the GPIO message handler
							gpioMessageHandler.open(threadPool);
						}
						break;
					case LLRP_CLIENT_CLOSED:
						// restart LLRP for a full reset
						llrpRuntimeData.setRestartServer(true);
						break;
					case LLRP_DATA_SENT:
					case RFC_CLIENT_OPENED:
					case RFC_CLIENT_CLOSED:
					case GPIO_CLIENT_OPENED:
					case GPIO_CLIENT_CLOSED:
						break;
					case CANCEL:
						break loop;
					}
					break;
				}
			} catch (Throwable t) {
				exception = t;
				if (retries == instancesProperties.getMaxStartupRetries()) {
					log.log(Level.SEVERE,
							"The LLRP service instance will be stopped due to an exception after "
									+ retries + " retries",
							exception);
					break loop;
				}
				retries++;
				llrpRuntimeData.setRestartServer(true);
			}
		}

		try {
			// stop LLRP incl. dependencies
			stopLLRP(llrpMessageHandler, rfcMessageHandler, gpioMessageHandler, cleanup);

			reportDepot.close();
			platform.close();
			platformManager.release(platform);
			instanceConfiguration.close();
			threadPool.shutdown();
		} catch (Throwable t) {
			if (exception != null) {
				exception = t;
			}
		} finally {
			fireCloseEvents(instanceId, llrpPort, exception, false /* isRestarting */);
		}
	}

	private void stopLLRP(LLRPMessageHandler llrpMessageHandler,
			RFCMessageHandler rfcMessageHandler, GPIOMessageHandler gpioMessageHandler,
			InstanceCleanup cleanup)
			throws LLRPUnknownChannelException, TCPConnectorStoppedException, InterruptedException,
			TimeoutException, TCPUnknownChannelException, ExecutionException, RFCException,
			UnsupportedSpecTypeException, UnsupportedAccessOperationException,
			UnsupportedAirProtocolException, EntityManagerException, InvalidMessageTypeException,
			InvalidParameterTypeException, PlatformException, GPIOException,
			InvalidIdentifierException, UtcClockException {
		// close and stop LLRP message handler
		if (llrpMessageHandler != null) {
			llrpMessageHandler.close();
		}
		// wait for missing RFC responses, remove all ROSpecs +
		// AccessSpecs + ROAccessReports and clear the queue
		llrpMessageHandler.resetConfiguration();
		cleanup.cleanUp(unexpectedTimeout * 1000);
		// close and stop the RFC message handler
		if (rfcMessageHandler != null) {
			rfcMessageHandler.close();
		}
		// close and stop the GPIO message handler
		if (gpioMessageHandler != null) {
			gpioMessageHandler.close();
		}
		// remove events enqueued between clean up and closing the
		// RFC + GPIO message handler
		eventQueue.clear();
	}

	private void processLLRPMessage(LLRPMessageEvent llrpMessageEvent, FSM<FSMEvent> fsm,
			FSMEvents fsmEvents) throws FSMGuardException, FSMActionException {
		fsmEvents.LLRP_MESSAGE_RECEIVED.setMessage(llrpMessageEvent.getMessage());
		fsm.fire(fsmEvents.LLRP_MESSAGE_RECEIVED);
		switch (llrpMessageEvent.getMessage().getMessageHeader().getMessageType()) {
		case GET_READER_CAPABILITIES:
			fsm.fire(fsmEvents.LLRP_GET_READER_CAPABILITIES_RECEIVED);
			break;
		case GET_READER_CONFIG:
			fsm.fire(fsmEvents.LLRP_GET_READER_CONFIG_RECEIVED);
			break;
		case SET_READER_CONFIG:
			fsm.fire(fsmEvents.LLRP_SET_READER_CONFIG_RECEIVED);
			break;
		case CLOSE_CONNECTION:
			fsm.fire(fsmEvents.LLRP_CLOSE_CONNECTION_RECEIVED);
			break;
		default:
			break;
		}
	}

	private void processLLRPMessage(LLRPMessageEvent llrpMessageEvent, FSM<FSMEvent> fsm,
			FSMEvents fsmEvents, LLRPRuntimeData llrpRuntimeData,
			LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			AccessSpecsManager accessSpecsManager) throws Exception {
		// get message from event
		Message llrpMessage = llrpMessageEvent.getMessage();
		MessageType llrpMessageType = llrpMessage.getMessageHeader().getMessageType();
		switch (llrpMessageType) {
		case GET_READER_CAPABILITIES:
		case GET_READER_CONFIG:
		case SET_READER_CONFIG:
		case CLOSE_CONNECTION:
			// process message via FSM
			processLLRPMessage(llrpMessageEvent, fsm, fsmEvents);
			return;
		default:
		}

		Message outgoingLLRPMessage = null;

		LLRPMessageCreator llrpMessageCreator = llrpRuntimeData.getMessageCreator();
		LLRPMessageValidator llrpMessageValidator = llrpRuntimeData.getMessageValidator();
		ROSpecsManager roSpecsManager = llrpRuntimeData.getRoSpecsManager();

		// Validate version of protocol
		LLRPStatus llrpStatus = llrpMessageValidator.validateProtocolVersion(llrpMessage,
				llrpRuntimeData.getProtocolVersion());

		// save current LLRP message (if a message already
		// exists then the current message is a KeepAliveAck)
		llrpRuntimeData.addCurrentMessage(llrpMessage, llrpStatus);

		switch (llrpMessageType) {
		case GET_SUPPORTED_VERSION:
			GetSupportedVersion getSupportedVersion = (GetSupportedVersion) llrpMessage;
			// see LLRP spec 9.1.1/9.1.2
			ProtocolVersion protocolVersion = llrpRuntimeData.isProtocolVersionNegotiated()
					? llrpRuntimeData.getProtocolVersion() : ProtocolVersion.LLRP_V1_1;
			llrpStatus = llrpMessageValidator.validateProtocolVersion(getSupportedVersion,
					protocolVersion);
			outgoingLLRPMessage = llrpMessageCreator.createResponse(getSupportedVersion,
					protocolVersion, llrpRuntimeData.getProtocolVersion(),
					llrpRuntimeData.SUPPORTED_PROTOCOL_VERSION, llrpStatus);
			break;
		case SET_PROTOCOL_VERSION:
			SetProtocolVersion setProtocolVersion = (SetProtocolVersion) llrpMessage;
			// see LLRP spec 9.1.3
			llrpStatus = llrpMessageValidator.validateProtocolVersion(setProtocolVersion,
					ProtocolVersion.LLRP_V1_1);
			// see LLRP spec 9.1.4
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				if (llrpRuntimeData.isProtocolVersionNegotiated()) {
					llrpStatus = llrpMessageCreator.createStatus(
							LLRPStatusCode.M_UNEXPECTED_MESSAGE,
							"The protocol version has already been set");
				} else {
					llrpStatus = llrpMessageValidator.validateSupportedVersion(setProtocolVersion,
							llrpRuntimeData.SUPPORTED_PROTOCOL_VERSION);
					if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
						// set negotiated protocol version to runtime data and
						// LLRP message handler
						llrpRuntimeData.setNegotiatedProtocolVersion(
								setProtocolVersion.getProtocolVersion());
						llrpRuntimeData.getMessageHandler()
								.setProtocolVersion(setProtocolVersion.getProtocolVersion());
					}
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(setProtocolVersion, llrpStatus);
			break;
		case ADD_ROSPEC:
			AddROSpec addRoSpec = (AddROSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				llrpStatus = llrpMessageValidator.validateSpecState(addRoSpec,
						ROSpecCurrentState.DISABLED);
				if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
					try {
						roSpecsManager.add(addRoSpec.getRoSpec());
					} catch (InvalidIdentifierException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
								e.getMessage());
					} catch (UnsupportedAccessOperationException | UnsupportedSpecTypeException
							| UnsupportedAirProtocolException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						llrpStatus = llrpMessageCreator.createStatus(
								LLRPStatusCode.P_UNSUPPORTED_PARAMETER, e.getMessage());
					} catch (Exception e) {
						String msg = "Cannot add ROSpec " + addRoSpec.getRoSpec().getRoSpecID();
						log.log(Level.SEVERE, msg, e);
						llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
								msg);
					}
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(addRoSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case DELETE_ROSPEC:
			DeleteROSpec deleteRoSpec = (DeleteROSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					roSpecsManager.setState(deleteRoSpec.getRoSpecID(),
							ROSpecCurrentState.DISABLED);
					roSpecsManager.remove(deleteRoSpec.getRoSpecID());
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot delete ROSpec " + deleteRoSpec.getRoSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(deleteRoSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case ENABLE_ROSPEC:
			EnableROSpec enableRoSpec = (EnableROSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					roSpecsManager.setState(enableRoSpec.getRoSpecID(),
							ROSpecCurrentState.INACTIVE);
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot enable ROSpec " + enableRoSpec.getRoSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(enableRoSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case DISABLE_ROSPEC:
			DisableROSpec disableRoSpec = (DisableROSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					roSpecsManager.setState(disableRoSpec.getRoSpecID(),
							ROSpecCurrentState.DISABLED);
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot disable ROSpec " + disableRoSpec.getRoSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(disableRoSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case START_ROSPEC:
			StartROSpec startRoSpec = (StartROSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					roSpecsManager.setState(startRoSpec.getRoSpecID(), ROSpecCurrentState.ACTIVE);
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot start ROSpec " + startRoSpec.getRoSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(startRoSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case STOP_ROSPEC:
			StopROSpec stopRoSpec = (StopROSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					roSpecsManager.setState(stopRoSpec.getRoSpecID(), ROSpecCurrentState.INACTIVE);
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot stop ROSpec " + stopRoSpec.getRoSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(stopRoSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case GET_ROSPECS:
			GetROSpecs getRoSpecs = (GetROSpecs) llrpMessage;
			outgoingLLRPMessage = llrpMessageCreator
					.createResponse(getRoSpecs, llrpRuntimeData.getProtocolVersion(),
							LLRPStatusCode.M_SUCCESS == llrpStatus.getStatusCode()
									? roSpecsManager.getROSpecs() : new ArrayList<ROSpec>(),
							llrpStatus);
			break;
		case ADD_ACCESSSPEC:
			AddAccessSpec addAccessSpec = (AddAccessSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				llrpStatus = llrpMessageValidator.validateSpecState(addAccessSpec, false);
				if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
					try {
						accessSpecsManager.add(addAccessSpec.getAccessSpec());
					} catch (InvalidIdentifierException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
								e.getMessage());
					} catch (UnsupportedAirProtocolException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						llrpStatus = llrpMessageCreator.createStatus(
								LLRPStatusCode.P_UNSUPPORTED_PARAMETER, e.getMessage());
					} catch (Exception e) {
						String msg = "Cannot add AccessSpec"
								+ addAccessSpec.getAccessSpec().getAccessSpecId();
						log.log(Level.SEVERE, msg, e);
						llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
								msg);
					}
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(addAccessSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case DELETE_ACCESSSPEC:
			DeleteAccessSpec deleteAccessSpec = (DeleteAccessSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					accessSpecsManager.remove(deleteAccessSpec.getAccessSpecID());
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot delete AccessSpec" + deleteAccessSpec.getAccessSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(deleteAccessSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case ENABLE_ACCESSSPEC:
			EnableAccessSpec enableAccessSpec = (EnableAccessSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					accessSpecsManager.setState(enableAccessSpec.getAccessSpecID(), /* enabled */
							true);
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot enable AccessSpec" + enableAccessSpec.getAccessSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(enableAccessSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case DISABLE_ACCESSSPEC:
			DisableAccessSpec disableAccessSpec = (DisableAccessSpec) llrpMessage;
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					accessSpecsManager.setState(disableAccessSpec.getAccessSpecID(), /* enabled */
							false);
				} catch (InvalidIdentifierException e) {
					log.log(Level.SEVERE, e.getMessage(), e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.A_INVALID,
							e.getMessage());
				} catch (Exception e) {
					String msg = "Cannot disable AccessSpec" + disableAccessSpec.getAccessSpecID();
					log.log(Level.SEVERE, msg, e);
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							msg);
				}
			}
			outgoingLLRPMessage = llrpMessageCreator.createResponse(disableAccessSpec,
					llrpRuntimeData.getProtocolVersion(), llrpStatus);
			break;
		case GET_ACCESSSPECS:
			GetAccessSpecs getAccessSpec = (GetAccessSpecs) llrpMessage;
			outgoingLLRPMessage = llrpMessageCreator.createResponse(getAccessSpec,
					llrpRuntimeData.getProtocolVersion(),
					LLRPStatusCode.M_SUCCESS == llrpStatus.getStatusCode()
							? accessSpecsManager.getAccessSpecs() : new ArrayList<AccessSpec>(),
					llrpStatus);
			break;
		case ENABLE_EVENTS_AND_REPORTS:
			llrpRuntimeData.getReaderConfig().getEventAndReports().setHold(false);
			// fall through
		case GET_REPORT:
			ROAccessReportCreator reportCreator = llrpRuntimeData.getROAccessReportCreator();
			if (llrpStatus.getStatusCode() == LLRPStatusCode.M_SUCCESS) {
				try {
					ROAccessReportDepot reportDepot = llrpRuntimeData.getROAccessReportDepot();
					// get all reports from depot
					List<ROAccessReportEntity> reportEntities = reportDepot
							.remove(reportDepot.getEntityIds());
					// create report
					outgoingLLRPMessage = reportCreator
							.accumulate(llrpRuntimeData.getProtocolVersion(), reportEntities);
				} catch (Exception e) {
					llrpStatus = llrpMessageCreator.createStatus(LLRPStatusCode.R_DEVICE_ERROR,
							"Cannot get reports");
				}
			}
			if (outgoingLLRPMessage == null) {
				String errorMsg = "Cannot create report: " + llrpStatus.getErrorDescription();
				log.log(Level.SEVERE, errorMsg);
				// send exception event
				boolean hold = llrpRuntimeData.getReaderConfig().getEventAndReports().getHold();
				if (!hold) {
					for (EventNotificationState state : llrpRuntimeData.getReaderConfig()
							.getReaderEventNotificationSpec().getEventNotificationStateList()) {
						if (state.isNotificationState()
								&& EventNotificationStateEventType.READER_EXCEPTION_EVENT == state
										.getEventType()) {
							ReaderExceptionEvent exceptionEvent = new ReaderExceptionEvent(
									new TLVParameterHeader((byte) 0), errorMsg);
							// create notification message
							Message msg = llrpRuntimeData.getMessageCreator().createNotification(
									exceptionEvent, llrpRuntimeData.getProtocolVersion(),
									llrpServiceInstanceRuntimeData.getPlatform());
							// send message
							llrpRuntimeData.getMessageHandler().requestSendingData(msg);
							break;
						}
					}
				}
				// create empty report as synchronous response
				outgoingLLRPMessage = reportCreator.accumulate(llrpRuntimeData.getProtocolVersion(),
						new ArrayList<ROAccessReportEntity>());
			}
			break;
		case CUSTOM_MESSAGE:
			outgoingLLRPMessage = llrpMessageCreator.createResponse((CustomMessage) llrpMessage,
					llrpRuntimeData.getProtocolVersion());
			break;
		case KEEPALIVE_ACK:
			// no response is sent => remove processed message here
			llrpRuntimeData.removeCurrentMessage(llrpMessage.getMessageHeader().getId());
			break;
		default:
		}
		if (outgoingLLRPMessage != null) {
			// remove processed message
			llrpRuntimeData.removeCurrentMessage(outgoingLLRPMessage.getMessageHeader().getId());
			// send outgoing LLRP message
			llrpRuntimeData.getMessageHandler().requestSendingData(outgoingLLRPMessage);
		}
	}

	private void processLLRPParameter(LLRPParameterEvent llrpParameter,
			LLRPRuntimeData llrpRuntimeData,
			LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData) throws Exception {
		Message outgoingLLRPMessage = null;
		boolean hold = llrpRuntimeData.getReaderConfig().getEventAndReports().getHold();
		switch (llrpParameter.getParameter().getParameterHeader().getParameterType()) {
		case RO_SPEC_EVENT:
			if (!hold) {
				// for each notification flag
				for (EventNotificationState state : llrpRuntimeData.getReaderConfig()
						.getReaderEventNotificationSpec().getEventNotificationStateList()) {
					// if notifications for event type shall be sent
					if (state.isNotificationState()
							&& EventNotificationStateEventType.ROSPEC_EVENT == state
									.getEventType()) {
						// create notification message
						outgoingLLRPMessage = llrpRuntimeData.getMessageCreator()
								.createNotification(llrpParameter.getParameter(),
										llrpRuntimeData.getProtocolVersion(),
										llrpServiceInstanceRuntimeData.getPlatform());
						break;
					}
				}
			}
			break;
		case RO_REPORT_SPEC: // sending of a ROAccessReport has been triggered
			if (!hold) {
				try {
					ROAccessReportDepot reportDepot = llrpRuntimeData.getROAccessReportDepot();
					ROReportSpecData roReportSpecData = (ROReportSpecData) llrpParameter.getData();
					// get reports for ROSpec from depot
					List<Entity<Object>> entities = reportDepot.acquire(reportDepot.getEntityIds());
					List<String> roSpecEntityIds = new ArrayList<>();
					for (Entity<Object> entity : entities) {
						ROAccessReportEntity reportEntity = (ROAccessReportEntity) entity
								.getObject();
						if (reportEntity.getRoSpecId() == roReportSpecData.getRoSpecId()) {
							roSpecEntityIds.add(entity.getEntityId());
						}
					}
					reportDepot.release(entities, false /* write */);
					// if reports exist
					if (!roSpecEntityIds.isEmpty()) {
						// remove reports from depot
						List<ROAccessReportEntity> reportEntities = reportDepot
								.remove(roSpecEntityIds);
						// create report
						outgoingLLRPMessage = llrpRuntimeData.getROAccessReportCreator()
								.accumulate(llrpRuntimeData.getProtocolVersion(), reportEntities);
					}
				} catch (Exception e) {
					String errorMsg = "Cannot create report";
					log.log(Level.SEVERE, errorMsg, e);
					for (EventNotificationState state : llrpRuntimeData.getReaderConfig()
							.getReaderEventNotificationSpec().getEventNotificationStateList()) {
						if (state.isNotificationState()
								&& EventNotificationStateEventType.READER_EXCEPTION_EVENT == state
										.getEventType()) {
							ReaderExceptionEvent exceptionEvent = new ReaderExceptionEvent(
									new TLVParameterHeader((byte) 0), errorMsg);
							// create notification message
							outgoingLLRPMessage = llrpRuntimeData.getMessageCreator()
									.createNotification(exceptionEvent,
											llrpRuntimeData.getProtocolVersion(),
											llrpServiceInstanceRuntimeData.getPlatform());
							break;
						}
					}
				}
			}
			break;
		default:
		}
		if (outgoingLLRPMessage != null) {
			// remove processed message
			llrpRuntimeData.removeCurrentMessage(outgoingLLRPMessage.getMessageHeader().getId());
			// send outgoing LLRP message
			llrpRuntimeData.getMessageHandler().requestSendingData(outgoingLLRPMessage);
		}
	}

	private void processRFCMessage(RFCMessageEvent rfcMessageEvent, FSM<FSMEvent> fsm,
			FSMEvents fsmEvents) throws FSMGuardException, FSMActionException {
		havis.llrpservice.sbc.rfc.message.MessageType messageType = rfcMessageEvent.getMessage()
				.getMessageHeader().getMessageType();
		switch (messageType) {
		case GET_CAPABILITIES_RESPONSE:
		case GET_CONFIGURATION_RESPONSE:
		case SET_CONFIGURATION_RESPONSE:
		case RESET_CONFIGURATION_RESPONSE:
		case EXECUTE_RESPONSE:
			fsmEvents.RFC_MESSAGE_RECEIVED.setMessage(rfcMessageEvent.getMessage());
			fsmEvents.RFC_MESSAGE_RECEIVED.setMessageData(rfcMessageEvent.getData());
			fsm.fire(fsmEvents.RFC_MESSAGE_RECEIVED);
			switch (messageType) {
			case GET_CAPABILITIES_RESPONSE:
				fsm.fire(fsmEvents.RFC_GET_CAPABILITIES_RESPONSE_RECEIVED);
				break;
			case GET_CONFIGURATION_RESPONSE:
				fsm.fire(fsmEvents.RFC_GET_CONFIGURATION_RESPONSE_RECEIVED);
				break;
			case SET_CONFIGURATION_RESPONSE:
				fsm.fire(fsmEvents.RFC_SET_CONFIGURATION_RESPONSE_RECEIVED);
				break;
			case RESET_CONFIGURATION_RESPONSE:
				fsm.fire(fsmEvents.RFC_RESET_CONFIGURATION_RESPONSE_RECEIVED);
				break;
			case EXECUTE_RESPONSE:
				fsm.fire(fsmEvents.RFC_EXECUTE_RESPONSE_RECEIVED);
				break;
			default:
			}
			break;
		default:
		}
	}

	private void processGPIOMessage(GPIOMessageEvent gpioMessageEvent, FSM<FSMEvent> fsm,
			FSMEvents fsmEvents) throws FSMGuardException, FSMActionException {
		havis.llrpservice.sbc.gpio.message.MessageType messageType = gpioMessageEvent.getMessage()
				.getMessageHeader().getMessageType();
		switch (messageType) {
		case GET_CONFIGURATION_RESPONSE:
		case SET_CONFIGURATION_RESPONSE:
		case RESET_CONFIGURATION_RESPONSE:
		case STATE_CHANGED:
			fsmEvents.GPIO_MESSAGE_RECEIVED.setMessage(gpioMessageEvent.getMessage());
			fsm.fire(fsmEvents.GPIO_MESSAGE_RECEIVED);
			switch (messageType) {
			case GET_CONFIGURATION_RESPONSE:
				fsm.fire(fsmEvents.GPIO_GET_CONFIGURATION_RESPONSE_RECEIVED);
				break;
			case SET_CONFIGURATION_RESPONSE:
				fsm.fire(fsmEvents.GPIO_SET_CONFIGURATION_RESPONSE_RECEIVED);
				break;
			case RESET_CONFIGURATION_RESPONSE:
				fsm.fire(fsmEvents.GPIO_RESET_CONFIGURATION_RESPONSE_RECEIVED);
				break;
			case STATE_CHANGED:
				fsm.fire(fsmEvents.GPIO_STATE_CHANGED_RECEIVED);
				break;
			default:
				break;
			}
			break;
		default:
		}
	}
}
