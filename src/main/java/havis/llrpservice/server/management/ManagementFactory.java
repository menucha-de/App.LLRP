package havis.llrpservice.server.management;

import havis.llrpservice.xml.properties.LLRPServerPropertiesType;
import havis.llrpservice.xml.properties.ManagementType;
import havis.llrpservice.xml.properties.ServicesType;

import java.util.ArrayList;
import java.util.List;

/**
 * A factory for creating management services.
 * <p>
 * This class is thread safe.
 * </p>
 */
public class ManagementFactory {

	private final LLRPServerPropertiesType serverProps;
	private final Object lock = new Object();

	public ManagementFactory(LLRPServerPropertiesType serverProps) {
		this.serverProps = serverProps;
	}

	/**
	 * Creates a management service.
	 * <p>
	 * The management service is thread safe.
	 * </p>
	 * 
	 * @return The management object
	 */
	public Management createManagement() {
		// create configured services
		final List<Management> services = new ArrayList<>();
		synchronized (lock) {
			ManagementType mgmt = serverProps.getManagement();
			if (mgmt != null) {
				ServicesType s = mgmt.getServices();
				if (s.getJMX() != null) {
					services.add(new JMXManagement());
				}
			}
		}
		// return a service which manages all created services
		return new Management() {
			@Override
			public synchronized void open() throws ManagementException {
				for (Management service : services) {
					service.open();
				}
			}

			@Override
			public synchronized void close() throws ManagementException {
				for (Management service : services) {
					service.close();
				}
			}

			@Override
			public synchronized void register(String path, Object mbean)
					throws ManagementException {
				for (Management service : services) {
					service.register(path, mbean);
				}
			}

			@Override
			public synchronized void unregister(String path)
					throws ManagementException {
				for (Management service : services) {
					service.unregister(path);
				}
			}
		};
	}
}
