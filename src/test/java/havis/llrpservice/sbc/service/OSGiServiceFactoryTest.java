package havis.llrpservice.sbc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.testng.Assert;
import org.testng.annotations.Test;

import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

public class OSGiServiceFactoryTest {

	private class Service {
	}

	@Test
	public void getService(@Mocked final BundleContext ctx, @Mocked final Logger log)
			throws Exception {
		final Map<ServiceTracker<Service, Object>, Service> services = new ConcurrentHashMap<>();
		final Map<Service, ServiceTracker<Service, Object>> serviceTrackers = new ConcurrentHashMap<>();
		new MockUp<ServiceTracker<Service, Object>>() {
			@Mock
			public void $init(BundleContext context, Filter filter,
					ServiceTrackerCustomizer<Service, Object> customizer) {
				Service service = new Service();
				services.put(this.getMockInstance(), service);
				serviceTrackers.put(service, this.getMockInstance());
			}

			@Mock
			public void open(boolean trackAllServices) {
			}

			@Mock
			public void removedService(ServiceReference<Service> reference, Object service) {
			}

			@Mock
			public Service addingService(ServiceReference<Service> reference) {
				return services.get(this.getMockInstance());
			}
		};

		new NonStrictExpectations() {
			{
				log.isLoggable(Level.INFO);
				result = true;
			}
		};

		// create a factory
		final OSGiServiceFactory<Service> serviceFactory = new OSGiServiceFactory<>(ctx,
				Service.class);
		Logger origLog = Deencapsulation.getField(serviceFactory, "log");
		Deencapsulation.setField(serviceFactory, "log", log);

		// start a thread for each port which shall provide a service instance
		final int portCount = 1;
		final int timeout = 3000;
		ExecutorService pool = Executors.newFixedThreadPool(portCount);
		List<Future<List<Service>>> futures = new ArrayList<>();
		final CountDownLatch startedThreadsLatch = new CountDownLatch(portCount);
		final CountDownLatch removedServicesLatch = new CountDownLatch(portCount);
		for (int i = 0; i < portCount; i++) {
			final int port = i;
			futures.add(pool.submit(new Callable<List<Service>>() {

				@Override
				public List<Service> call() throws Exception {
					startedThreadsLatch.countDown();
					List<Service> ret = new ArrayList<>();
					// wait for service instances
					Service service = null;
					for (int i = 0; i <= port; i++) {
						service = serviceFactory.getService("host", port, timeout);
						ret.add(service);
					}
					// send removedService event
					ServiceTracker<Service, Object> serviceTracker = serviceTrackers.get(service);
					serviceTracker.removedService(null, null);
					removedServicesLatch.countDown();
					// wait for service instances
					for (int i = 0; i <= port; i++) {
						service = serviceFactory.getService("host", port, timeout);
						ret.add(service);
					}
					return ret;
				}
			}));
		}
		// wait for blocked "getService" calls
		startedThreadsLatch.await(3000, TimeUnit.MILLISECONDS);
		Thread.sleep(500);
		// fire an addingService event to each thread/port
		for (Entry<ServiceTracker<Service, Object>, Service> entry : services.entrySet()) {
			Assert.assertEquals(entry.getValue(), entry.getKey().addingService(null));
		}
		// wait for removedService events
		removedServicesLatch.await(3000, TimeUnit.MILLISECONDS);
		// fire an addingService event to each thread/port
		for (Entry<ServiceTracker<Service, Object>, Service> entry : services.entrySet()) {
			Assert.assertEquals(entry.getValue(), entry.getKey().addingService(null));
		}
		// for each thread/port
		for (Future<List<Service>> future : futures) {
			// get service instances
			List<Service> serviceList = future.get(3000000, TimeUnit.MILLISECONDS);
			// for each service instance
			for (Service service : serviceList) {
				// release the service instance at the service factory
				// the last call also releases the service tracker
				serviceFactory.release(service);
			}
		}

		pool.shutdown();

		final List<String> messages = new ArrayList<>();
		new Verifications() {
			{
				log.log(Level.INFO, withCapture(messages));
				times = portCount * 5;
			}
		};
		for (int portNo = 0; portNo < portCount; portNo++) {
			int openedCount = 0;
			int addedCount = 0;
			int removedCount = 0;
			int closedCount = 0;
			String filter = "(&(" + Constants.OBJECTCLASS + "=" + Service.class.getName() + "))";
			for (String msg : messages) {
				if (msg.startsWith("Opened service tracker with filter " + filter)) {
					openedCount++;
				} else if (msg.startsWith("Get service for filter " + filter)) {
					addedCount++;
				} else if (msg.startsWith("Lost service for filter " + filter)) {
					removedCount++;
				} else if (msg.startsWith("Closed service tracker with filter " + filter)) {
					closedCount++;
				}
			}

			Assert.assertEquals(openedCount, 1);
			Assert.assertEquals(addedCount, 2);
			Assert.assertEquals(removedCount, 1);
			Assert.assertEquals(closedCount, 1);
		}

		Deencapsulation.setField(serviceFactory, "log", origLog);
	}

	@Test
	public void getServiceError(@Capturing final BundleContext ctx,
			@Mocked final ServiceTracker<Service, Object> serviceTracker, @Mocked final Logger log)
			throws Exception {
		OSGiServiceFactory<Service> factory = new OSGiServiceFactory<>(ctx, Service.class);
		long start = System.currentTimeMillis();
		try {
			factory.getService("host", 3 /* port */, 500 /* timeout */);
		} catch (ServiceFactoryException e) {
			Assert.assertTrue(e.getCause() instanceof TimeoutException
					&& e.getCause().getMessage().contains("500 ms"));
			Assert.assertTrue(System.currentTimeMillis() - start >= 500);
		}
	}
}
