package havis.llrpservice.sbc.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The service factory provides services which are available via OSGi. The
 * services are searched using an OSGi filter <code>(&(objectClass=x))</code>.
 * The object class is the class name of the generic type <code>T</code> (eg.
 * <code>java.lang.Integer</code>).
 * 
 * @param <T>
 */
public class OSGiServiceFactory<T> implements ServiceFactory<T> {

	private static final Logger log = Logger.getLogger(OSGiServiceFactory.class.getName());

	private final BundleContext bundleContext;
	private final Class<T> clazz;
	private final ReentrantLock lock;
	private Map<String, ServiceData> services = new HashMap<>();

	class ServiceData {
		String serviceFilter;
		ServiceTracker<T, Object> serviceTracker;
		// the currently available service object provided by the service
		// tracker
		T currService;
		// the service objects returned by the getService method which has not
		// been released yet
		List<T> unreleasedServices = new ArrayList<>();
		Condition isAdded = lock.newCondition();
	}

	/**
	 * @param ctx
	 * @param clazz
	 */
	public OSGiServiceFactory(BundleContext ctx, Class<T> clazz) {
		this(ctx, clazz, new ReentrantLock());
	}

	/**
	 * @param ctx
	 * @param clazz
	 * @param lock
	 *            the object which is used internally for synchronization
	 */
	public OSGiServiceFactory(BundleContext ctx, Class<T> clazz, ReentrantLock lock) {
		this.bundleContext = ctx;
		this.clazz = clazz;
		this.lock = lock;
	}

	@Override
	public T getService(final String host, final int port, long timeout)
			throws ServiceFactoryException {
		lock.lock();
		try {
			Date timeoutEnd = new Date(System.currentTimeMillis() + timeout);
			final ServiceData serviceData = getServiceData(host, port);
			if (serviceData.serviceTracker == null) {
				serviceData.serviceFilter = String.format("(&(%s=%s))", Constants.OBJECTCLASS,
						clazz.getName());
				serviceData.serviceTracker = new ServiceTracker<T, Object>(bundleContext,
						bundleContext.createFilter(serviceData.serviceFilter),
						null /* ServiceTrackerCustomizer */) {

					@SuppressWarnings("unchecked")
					@Override
					public Object addingService(ServiceReference<T> reference) {
						lock.lock();
						try {
							T addedService = (T) super.addingService(reference);
							// set service to service data
							serviceData.currService = addedService;
							// notify waiting threads
							serviceData.isAdded.signalAll();
							if (log.isLoggable(Level.INFO)) {
								log.log(Level.INFO,
										"Get service for filter " + serviceData.serviceFilter);
							}
							return addedService;
						} finally {
							lock.unlock();
						}
					}

					@Override
					public void removedService(ServiceReference<T> reference,
							Object removedService) {
						lock.lock();
						try {
							super.removedService(reference, removedService);
							// remove service from service data
							serviceData.currService = null;
							log.log(Level.INFO,
									"Lost service for filter " + serviceData.serviceFilter);
						} finally {
							lock.unlock();
						}
					}
				};
				serviceData.serviceTracker.open();
				if (log.isLoggable(Level.INFO)) {
					log.log(Level.INFO,
							"Opened service tracker with filter " + serviceData.serviceFilter);
				}
			}
			while (serviceData.currService == null) {
				if (!serviceData.isAdded.awaitUntil(timeoutEnd)) {
					throw new TimeoutException(
							String.format("Cannot get service for filter %s within %d ms",
									serviceData.serviceFilter, timeout));
				}
			}
			serviceData.unreleasedServices.add(serviceData.currService);
			return serviceData.currService;
		} catch (Exception e) {
			// remove the empty service data
			ServiceData serviceData = getServiceData(host, port);
			if (serviceData != null && serviceData.unreleasedServices.isEmpty()) {
				services.remove(getKey(host, port));
			}
			throw new ServiceFactoryException(
					String.format("Cannot get service for filter (&(%s=%s))", Constants.OBJECTCLASS,
							clazz.getName()),
					e);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void release(T service) {
		lock.lock();
		try {
			// for each service
			List<String> keys = new ArrayList<>();
			for (Entry<String, ServiceData> s : services.entrySet()) {
				ServiceData serviceData = s.getValue();
				// if the service matches and it is not used any longer
				if (serviceData.unreleasedServices.remove(service)
						&& serviceData.unreleasedServices.isEmpty()) {
					// close relating service tracker
					serviceData.serviceTracker.close();
					if (log.isLoggable(Level.INFO)) {
						log.log(Level.INFO,
								"Closed service tracker with filter " + serviceData.serviceFilter);
					}
					keys.add(s.getKey());
				}
			}
			// remove unused service data
			for (String key : keys) {
				services.remove(key);
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Returns the service data for host/port combination from map
	 * {@link #services}. If it does not exist yet then an empty data structure
	 * is created.
	 * 
	 * @param host
	 * @param port
	 * @return The service data
	 */
	private ServiceData getServiceData(String host, int port) {
		String key = getKey(host, port);
		ServiceData serviceData = services.get(key);
		if (serviceData == null) {
			serviceData = new ServiceData();
			services.put(key, serviceData);
		}
		return serviceData;
	}

	/**
	 * Returns the key for a host/port combination. The key is used for getting
	 * a service from map {@link #services}.
	 * 
	 * @param host
	 * @param port
	 * @return The key
	 */
	private String getKey(String host, int port) {
		return host + ":" + port;
	}
}
