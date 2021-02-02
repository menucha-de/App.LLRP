package havis.llrpservice.sbc.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The service factory provides services which are created via Java Reflection
 * API.
 * 
 * @param <T>
 */
public class ReflectionServiceFactory<T> implements ServiceFactory<T> {

	private Class<T> clazz;
	private String addressSetterMethodName;
	private ReentrantLock lock;
	private Map<String, ServiceData> services = new HashMap<>();

	private class ServiceData {
		T currService;
		// the service objects returned by the getService method which has not
		// been released yet
		List<T> unreleasedServices = new ArrayList<>();
	}

	/**
	 * @param serviceClassName
	 *            the class name like <code>java.lang.Integer</code> for which
	 *            an object shall be created via Java Reflection API and
	 *            provided as service
	 * @param addressSetterMethodName
	 *            The name of a method with parameters host (String) and port
	 *            (int). The method is called directly after the creation of an
	 *            object to set the values as identifier.
	 * @throws ClassNotFoundException
	 */
	public ReflectionServiceFactory(String serviceClassName,
			String addressSetterMethodName) throws ClassNotFoundException {
		this(serviceClassName, addressSetterMethodName, new ReentrantLock());
	}

	/**
	 * @param serviceClassName
	 *            the class name like <code>java.lang.Integer</code> for which
	 *            an object shall be created via Java Reflection API and
	 *            provided as service
	 * @param addressSetterMethodName
	 *            The name of a method with parameters host (String) and port
	 *            (int). The method is called directly after the creation of an
	 *            object to set the values as identifier.
	 * @param lock
	 *            the object which is used internally for synchronization
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public ReflectionServiceFactory(String serviceClassName,
			String addressSetterMethodName, ReentrantLock lock)
			throws ClassNotFoundException {
		this.clazz = (Class<T>) Class.forName(serviceClassName);
		this.addressSetterMethodName = addressSetterMethodName;
		this.lock = lock;
	}

	@Override
	public T getService(String host, int port, long timeout)
			throws ServiceFactoryException {
		lock.lock();
		try {
			ServiceData serviceData = getServiceData(host, port);
			if (serviceData.currService == null) {
				serviceData.currService = clazz.newInstance();

				if (addressSetterMethodName != null) {
					Method method = clazz.getMethod(addressSetterMethodName,
							String.class, int.class);
					method.invoke(serviceData.currService, host, port);
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
			throw new ServiceFactoryException(String.format(
					"Cannot create service for host '%s' and port %d", host,
					port), e);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void release(T service) throws ServiceFactoryException {
		lock.lock();
		try {
			// for each service
			List<String> keys = new ArrayList<>();
			for (Entry<String, ServiceData> s : services.entrySet()) {
				ServiceData serviceData = s.getValue();
				// if the service matches and it is not used any longer
				if (serviceData.unreleasedServices.remove(service)
						&& serviceData.unreleasedServices.isEmpty()) {
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
