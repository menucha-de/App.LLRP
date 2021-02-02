package havis.llrpservice.server.llrp;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
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

import havis.llrpservice.common.concurrent.EventPipe;
import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.tcp.TCPConnectorStoppedException;
import havis.llrpservice.common.tcp.TCPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPServerMultiplexed;
import havis.llrpservice.csc.llrp.LLRPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.Keepalive;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.ConnectionCloseEvent;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.event.EventPriority;
import havis.llrpservice.server.event.EventQueue;
import havis.llrpservice.server.event.LLRPMessageEvent;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.xml.configuration.AddressGroup;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

/**
 * The LLRPMessageHandler handles the communication to LLRP clients. Only one
 * client is accepted. Further client connections are rejected.
 * <p>
 * A server channel is opened with {@link #open(Platform, ExecutorService)} and
 * closed with {@link #close()}. Before the message handler can be opened again
 * the method {@link #run} must have been started and stopped in a separate
 * thread.
 * </p>
 * <p>
 * Received LLRP messages from the client are put to an event queue.
 * </p>
 */
public class LLRPMessageHandler implements Runnable {

	private static final Logger log = Logger.getLogger(LLRPMessageHandler.class.getName());

	private AddressGroup llrpAddress;
	private final int openCloseTimeout;

	private final LLRPServerMultiplexed llrpServer;
	private final EventQueue queue;
	// Server handling
	private LLRPServerEventHandler serverEventHandler;
	// Server channel to open and close a server connection
	private ServerSocketChannel serverChannel;

	private ReentrantLock lock = new ReentrantLock();

	private enum RunLatchEvent {
		OPENED, CLOSED
	}

	private EventPipe<RunLatchEvent> runLatch = new EventPipe<>(lock);

	private List<LLRPMessageHandlerListener> listeners = new CopyOnWriteArrayList<LLRPMessageHandlerListener>();

	private Future<?> thread;
	private boolean hasThreadCompleted;

	// configuration data
	private ProtocolVersion protocolVersion;
	private long keepAliveInterval;
	private long keepAliveStopTimeout;

	/**
	 * @param serverConfiguration
	 * @param instanceConfiguration
	 * @param queue
	 *            event queue for received LLRP messages (enqueued as
	 *            {@link LLRPMessageEvent})
	 * @param tcpServerLLRP
	 * @throws EntityManagerException
	 * @throws ConfigurationException
	 * @throws PersistenceException
	 * @throws IOException
	 */
	public LLRPMessageHandler(ServerConfiguration serverConfiguration,
			ServerInstanceConfiguration instanceConfiguration, EventQueue queue,
			TCPServerMultiplexed tcpServerLLRP) throws EntityManagerException,
			ConfigurationException, PersistenceException, IOException {
		// create config analyser
		Entity<LLRPServerConfigurationType> serverConfigEntity = serverConfiguration.acquire();
		Entity<LLRPServerInstanceConfigurationType> instanceConfigEntity = instanceConfiguration
				.acquire();
		LLRPConfigAnalyser llrpConfigAnalyser = new LLRPConfigAnalyser(
				serverConfigEntity.getObject());
		llrpConfigAnalyser.setServerInstanceConfig(instanceConfigEntity.getObject());
		instanceConfiguration.release(instanceConfigEntity, false /* write */);
		serverConfiguration.release(serverConfigEntity, false /* write */);

		llrpAddress = llrpConfigAnalyser.getAddress();
		openCloseTimeout = llrpConfigAnalyser.getOpenCloseTimeout();
		llrpServer = new LLRPServerMultiplexed(tcpServerLLRP);
		this.queue = queue;

		resetConfiguration();
	}

	/**
	 * Resets the configuration. See
	 * {@link #setProtocolVersion(ProtocolVersion)},
	 * {@link #setKeepaliveInterval(long, long)}.
	 */
	public void resetConfiguration() {
		protocolVersion = ProtocolVersion.LLRP_V1_0_1;
		keepAliveInterval = 0;
		keepAliveStopTimeout = 0;
	}

	public String getConnectionLocalHostAddress() {
		lock.lock();
		try {
			if (serverEventHandler != null) {
				return serverEventHandler.getConnectionLocalHostAddress();
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
		this.protocolVersion = protocolVersion;
		lock.lock();
		try {
			if (serverEventHandler != null) {
				serverEventHandler.setProtocolVersion(protocolVersion);
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
		keepAliveInterval = interval;
		keepAliveStopTimeout = stopTimeout;
		lock.lock();
		try {
			if (serverEventHandler != null) {
				serverEventHandler.setKeepaliveInterval(interval, stopTimeout);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Adds a listener.
	 * 
	 * @param listener
	 */
	public void addListener(LLRPMessageHandlerListener listener) {
		listeners.add(listener);
		lock.lock();
		try {
			if (serverEventHandler != null) {
				serverEventHandler.addListener(listener);
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
			if (serverEventHandler != null) {
				serverEventHandler.removeListener(listener);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Opens a server channel synchronously. If it is opened and {@link #run()}
	 * has been started a {@link LLRPMessageHandlerListener#opened()} event is
	 * sent to registered listeners.
	 * 
	 * @param platformController
	 *            platform controller for getting UTC clock flag and platform
	 *            uptime
	 * @param threadPool
	 * @throws IOException
	 * @throws TCPConnectorStoppedException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 *             Time out while opening the server channel
	 */
	public void open(Platform platformController, ExecutorService threadPool)
			throws IOException, TCPConnectorStoppedException, InterruptedException,
			ExecutionException, TimeoutException {
		thread = threadPool.submit(this);
		lock.lock();
		try {
			hasThreadCompleted = false;
			// create server handler
			serverEventHandler = new LLRPServerEventHandler(llrpServer, platformController,
					listeners);
			serverEventHandler.setProtocolVersion(protocolVersion);
			serverEventHandler.setKeepaliveInterval(keepAliveInterval, keepAliveStopTimeout);
			// request the opening of the server channel
			llrpServer.requestOpeningChannel(llrpAddress.getHost(), llrpAddress.getPort(),
					serverEventHandler);
			// wait for opened channel
			try {
				serverChannel = serverEventHandler.awaitServerOpening(openCloseTimeout);
			} catch (TimeoutException e) {
				throw new TimeoutException(
						"Unable to open the server channel within " + openCloseTimeout + "ms");
			}
			// start handling of messages
			runLatch.fire(RunLatchEvent.OPENED);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Closes the server channel synchronously. An existing client channel is
	 * canceled and method {@link #run} is stopped. Before {@link #run} stops a
	 * {@link LLRPMessageHandlerListener#closed(havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent)}
	 * event is sent to registered listeners.
	 * 
	 * @throws LLRPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InterruptedException
	 * @throws InvalidMessageTypeException
	 * @throws InvalidParameterTypeException
	 * @throws TimeoutException
	 *             time out for closing the server channel
	 * @throws PlatformException
	 * @throws ExecutionException
	 */
	public void close() throws LLRPUnknownChannelException, TCPConnectorStoppedException,
			InterruptedException, InvalidMessageTypeException, InvalidParameterTypeException,
			TimeoutException, PlatformException, ExecutionException {
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
			if (serverChannel != null) {
				// cancel the execution of the server event handler
				// (a blocked "awaitReceivedData" call in "run" method is
				// released)
				serverEventHandler.cancelExecution();
				// close the server channel
				try {
					llrpServer.requestClosingChannel(serverChannel, false /* force */);
					try {
						timeout = end - System.currentTimeMillis();
						serverEventHandler.awaitServerClosing(timeout < 1 ? 1 : timeout);
					} catch (TimeoutException e) {
						throw new TimeoutException(
								"Unable to close the server channel within " + timeout + "ms");
					}
				} catch(LLRPUnknownChannelException e) {
					log.log(Level.INFO, "Channel already closed!", e);
				}
				serverChannel = null;
				serverEventHandler = null;
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
	 * Requests the sending of a LLRP message to the connected client.
	 * <p>
	 * {@link LLRPMessageHandlerListener#dataSent(LLRPDataSentEvent)} is called
	 * for all listeners after the data has been sent.
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
			// forward the message to server handler
			serverEventHandler.requestSendingData(message);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Runs the message handler. The methods blocks until the message handler is
	 * closed with {@link #close()}.
	 */
	@Override
	public void run() {
		Throwable throwable = null;
		try {
			LLRPServerEventHandler serverHandlerLocal;
			lock.lock();
			try {
				// block until the "open" or "close" method has been called
				List<RunLatchEvent> events = runLatch.await(EventPipe.NO_TIMEOUT);
				// if message handler was closed
				if (events.contains(RunLatchEvent.CLOSED)) {
					return;
				}
				serverHandlerLocal = serverEventHandler;
			} finally {
				lock.unlock();
			}
			// fire "opened" events
			for (LLRPMessageHandlerListener listener : listeners) {
				listener.opened();
			}
			while (true) {
				// wait for next message
				Message message = serverHandlerLocal.awaitReceivedData();
				// if message handler was closed
				if (message == null) {
					break;
				}
				// enqueue the received message
				queue.put(new LLRPMessageEvent(message), EventPriority.LLRP);
			}
		} catch (Throwable t) {
			throwable = t;
		} finally {
			if (listeners.size() == 0) {
				if (throwable != null) {
					log.log(Level.SEVERE, "Execution stopped due to an exception", throwable);
				}
			} else {
				// fire "closed" events
				for (LLRPMessageHandlerListener listener : listeners) {
					listener.closed(throwable);
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
}
