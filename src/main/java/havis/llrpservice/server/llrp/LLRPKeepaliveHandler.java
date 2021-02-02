package havis.llrpservice.server.llrp;

import havis.llrpservice.data.message.Keepalive;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The LLRPKeepaliveHandler sends {@link Keepalive} messages in defined
 * intervals to LLRP clients.
 * <p>
 * With method {@link #start()} a thread is started which sends the keep alive
 * messages. The first message is sent at the end of the first interval.
 * </p>
 * <p>
 * A sent keepalive message must be acknowledged with
 * {@link #setAcknowledged(boolean)} after the client has received the message.
 * If a keepalive message is not acknowledged by the client within the next
 * keepalive interval then the connection to the client is closed by aborting
 * the execution of the server event handler with an exception.
 * </p>
 * <p>
 * The thread must be stopped with {@link #stop(long)}.
 * </p>
 */
public class LLRPKeepaliveHandler implements Runnable {

	private static final Logger log = Logger.getLogger(LLRPKeepaliveHandler.class.getName());

	private long interval; // in ms
	private LLRPConnectionHandler connection;
	ExecutorService threadPool;
	Future<?> keepaliveThread = null;
	private final Lock lock = new ReentrantLock();
	private Condition condition = lock.newCondition();
	boolean stopped = false;
	boolean acknowledged = true;

	/**
	 * @param connection
	 *            connection to the client
	 * @param interval
	 *            keep alive interval in milliseconds
	 */
	public LLRPKeepaliveHandler(LLRPConnectionHandler connection, long interval) {
		this.connection = connection;
		this.interval = interval;
	}

	/**
	 * Starts the keep alive thread. The first message is sent at the end of the
	 * first interval.
	 */
	public void start() {
		threadPool = Executors.newFixedThreadPool(1);
		keepaliveThread = threadPool.submit(this);
	}

	/**
	 * Stops the keep alive thread.
	 * 
	 * @param stopTimeout
	 *            time out in milliseconds
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public void stop(long stopTimeout)
			throws InterruptedException, ExecutionException, TimeoutException {
		lock.lock();
		try {
			stopped = true;
			condition.signal();
		} finally {
			lock.unlock();
		}
		if (keepaliveThread != null) {
			keepaliveThread.get(stopTimeout, TimeUnit.MILLISECONDS);
			keepaliveThread = null;
			threadPool.shutdown();
		}
	}

	/**
	 * Sets the acknowledgment state of a keep alive message.
	 * <p>
	 * If the state is set to <code>true</code> then the next keep alive message
	 * is sent at the end of the current interval else the connection to the
	 * client is closed.
	 * </p>
	 * 
	 * @param acknowledged
	 */
	public void setAcknowledged(boolean acknowledged) {
		lock.lock();
		try {
			this.acknowledged = acknowledged;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void run() {
		Date waitUntil = new Date();
		lock.lock();
		try {
			while (!stopped) {
				waitUntil = new Date(waitUntil.getTime() + interval);
				while (!stopped) {
					if (!condition.awaitUntil(waitUntil)) {
						// time out
						break;
					}
				}
				if (!stopped) {
					if (acknowledged) {
						acknowledged = false;
						connection.requestSendingKeepaliveMessage();
					} else {
						// abort execution with an exception and stop the thread
						connection.getServerEventHandler().abortExecution(new Exception(
								"Missing keep alive acknowledge after " + interval + " ms"));
						stopped = true;
					}
				}
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, "Keepalive thread closed with exception ", e);
			}
		} finally {
			lock.unlock();
		}
	}
}
