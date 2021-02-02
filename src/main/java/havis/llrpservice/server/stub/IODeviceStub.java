package havis.llrpservice.server.stub;

import havis.device.io.Configuration;
import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.IOConsumer;
import havis.device.io.IODevice;
import havis.device.io.State;
import havis.device.io.StateEvent;
import havis.device.io.Type;
import havis.device.io.exception.ConnectionException;
import havis.device.io.exception.ImplementationException;
import havis.device.io.exception.ParameterException;
import havis.llrpservice.common.concurrent.EventPipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rits.cloning.Cloner;

public class IODeviceStub implements IODevice {

	private Logger log = Logger.getLogger(IODeviceStub.class.getName());

	private final long GPI_PORT_CHANGE_INTERVAL = 200; // ms
	private final long GET_CONFIGURATION_DELAY = 20; // ms
	private final Map<Short, IOConfiguration> DFLT_CONFS = new HashMap<>();

	private final List<Configuration> confs = new ArrayList<>();
	private final Object confsLock = new Object();
	private final EventPipe<StateEvent> stateEvents = new EventPipe<>(
			new ReentrantLock());
	private final Object consumerLock = new Object();
	private IOConsumer consumer;
	private ScheduledExecutorService threadPool = Executors
			.newScheduledThreadPool(1);
	private Future<?> future;

	public IODeviceStub() throws ImplementationException, ConnectionException {
		addDfltConf(1 /* portId */, Direction.INPUT, State.LOW, true /* gpiEventsEnabled */);
		addDfltConf(2 /* portId */, Direction.OUTPUT, State.LOW, false /* gpiEventsEnabled */);
		addDfltConf(3 /* portId */, Direction.INPUT, State.LOW, false /* gpiEventsEnabled */);
		addDfltConf(4 /* portId */, Direction.OUTPUT, State.LOW, false /* gpiEventsEnabled */);
		resetConfiguration(false /* sendEvents */);
	}

	@Override
	public void openConnection(IOConsumer consumer, int timeout)
			throws ConnectionException, ImplementationException {
		synchronized (consumerLock) {
			this.consumer = consumer;
		}
		future = threadPool.scheduleAtFixedRate(
				new Runnable() {

					@Override
					public void run() {
						try {
							List<Configuration> configuration = getConfiguration(
									Type.IO, (short) 0 /* pinId */);
							for (Configuration conf : configuration) {
								if (conf instanceof IOConfiguration) {
									IOConfiguration ioConf = (IOConfiguration) conf;
									if (ioConf.isEnable()
											&& Direction.INPUT == ioConf
													.getDirection()) {
										// change state of GPI
										ioConf.setState(ioConf.getState() == State.HIGH ? State.LOW
												: State.HIGH);
									}
								}
							}
							setConfiguration(configuration);
						} catch (Exception e) {
							log.log(Level.SEVERE, "Cannot change GPI states", e);
						}
					}
				}, 0 /* delay */, GPI_PORT_CHANGE_INTERVAL /* period */,
				TimeUnit.MILLISECONDS);
	}

	@Override
	public void closeConnection() throws ConnectionException,
			ImplementationException {
		if (future != null) {
			future.cancel(false /* mayInterruptIfRunning */);
			future = null;
		}
		synchronized (consumerLock) {
			consumer = null;
		}
	}

	@Override
	public List<Configuration> getConfiguration(Type type, short pin)
			throws ConnectionException, ParameterException,
			ImplementationException {
		try {
			Thread.sleep(GET_CONFIGURATION_DELAY);
		} catch (InterruptedException e) {
			throw new ImplementationException(
					"Sleeping in 'getConfiguration' was interrupted", e);
		}
		switch (type) {
		case ALL:
		case IO:
			synchronized (confsLock) {
				List<Configuration> ret = null;
				if (pin == 0) {
					ret = confs;
				} else {
					ret = new ArrayList<>();
					for (Configuration conf : confs) {
						if (conf instanceof IOConfiguration) {
							IOConfiguration ioConf = (IOConfiguration) conf;
							if (ioConf.getId() == pin) {
								ret.add(ioConf);
								break;
							}
						}
					}
				}
				return new Cloner().deepClone(ret);
			}
		case KEEP_ALIVE:
		}
		return new ArrayList<>();
	}

	@Override
	public void setConfiguration(List<Configuration> configuration)
			throws ConnectionException, ParameterException,
			ImplementationException {
		synchronized (confsLock) {
			// for each new configuration
			for (int i = 0; i < configuration.size(); i++) {
				Configuration conf = configuration.get(i);
				if (conf instanceof IOConfiguration) {
					IOConfiguration ioConf = (IOConfiguration) conf;
					// update configuration
					if (!updatePin(ioConf, true /* sendEvents */)) {
						throw new ParameterException("Unknown pin id "
								+ ioConf.getId());
					}
				}
			}
		}
		fireStateEvents();
	}

	@Override
	public void resetConfiguration() throws ImplementationException,
			ConnectionException, ImplementationException {
		synchronized (confsLock) {
			resetConfiguration(true /* sendEvents */);
		}
		fireStateEvents();
	}

	private void addDfltConf(int portId, Direction dir, State state,
			boolean isGpiEventsEnabled) {
		DFLT_CONFS.put((short) portId, new IOConfiguration((short) portId, dir,
				state, isGpiEventsEnabled));
	}

	private void resetConfiguration(boolean sendEvents) {
		Cloner cloner = new Cloner();
		if (sendEvents) {
			for (Configuration conf : confs) {
				if (conf instanceof IOConfiguration) {
					IOConfiguration ioConf = (IOConfiguration) conf;
					IOConfiguration dfltConf = DFLT_CONFS.get(ioConf.getId());
					if (dfltConf != null) {
						updatePin(cloner.deepClone(dfltConf), sendEvents);
					}
				}
			}
		}
		confs.clear();
		confs.addAll(cloner.deepClone(DFLT_CONFS.values()));
	}

	private boolean updatePin(IOConfiguration c, boolean sendEvents) {
		// for each existing configuration
		for (int i = 0; i < confs.size(); i++) {
			Configuration conf = confs.get(i);
			if (conf instanceof IOConfiguration) {
				IOConfiguration ioConf = (IOConfiguration) conf;
				// if same id
				if (ioConf.getId() == c.getId()) {
					// replace configuration
					confs.set(i, c);
					// if pin is enabled and the state has changed
					if (Direction.INPUT == ioConf.getDirection()
							&& Direction.INPUT == c.getDirection()
							&& ioConf.isEnable() && c.isEnable()
							&& ioConf.getState() != c.getState()) {
						// add state event
						stateEvents
								.fire(new StateEvent(c.getId(), c.getState()));
					}
					return true;
				}
			}
		}
		return false;
	}

	private void fireStateEvents() {
		// fire all existing events
		List<StateEvent> events = null;
		do {
			try {
				events = stateEvents.await(EventPipe.RETURN_IMMEDIATELY);
			} catch (Exception e) {
				// impossible
			}
			synchronized (consumerLock) {
				if (consumer != null) {
					for (StateEvent event : events) {
						consumer.stateChanged(event);
					}
				}
			}
		} while (events.size() > 0);
	}
}
