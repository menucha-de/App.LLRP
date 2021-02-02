package havis.llrpservice.server.management;

import havis.llrpservice.server.management.bean.Server;
import havis.llrpservice.xml.properties.JmxType;
import havis.llrpservice.xml.properties.LLRPServerPropertiesType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ManagementFactoryTest {

	@Test
	public void createManagementUnconfigured(
			@Mocked final LLRPServerPropertiesType serverProps,
			@Mocked final JMXManagement jmxMgmt) throws ManagementException {
		new NonStrictExpectations() {
			{
				serverProps.getManagement();
				result = null;
			}
		};
		// create a management instance without configured management
		ManagementFactory mf = new ManagementFactory(serverProps);
		Management mgmt = mf.createManagement();
		// open the returned service
		mgmt.open();
		// nothing happens
		new Verifications() {
			{
				jmxMgmt.open();
				times = 0;
			}
		};
	}

	@Test
	public void createManagementConfigured(
			@Mocked final LLRPServerPropertiesType serverProps,
			@Mocked final JmxType jmxType, @Mocked final JMXManagement jmxMgmt,
			@Mocked final Server mbean) throws Exception {
		new NonStrictExpectations() {
			{
				serverProps.getManagement().getServices().getJMX();
				result = jmxType;
			}
		};
		// create multiple management instance with configured management
		// and call all methods of the management interface
		final ManagementFactory mf = new ManagementFactory(serverProps);
		Management mgmt1 = mf.createManagement();
		mgmt1.open();
		mgmt1.register("/a/b", mbean);

		Management mgmt2 = mf.createManagement();
		mgmt2.open();
		mgmt2.register("/x/y", mbean);

		mgmt1.unregister("/a/b");
		mgmt1.close();

		mgmt2.unregister("/x/y");
		mgmt2.close();
		// the configured service is called
		new Verifications() {
			{
				jmxMgmt.open();
				times = 2;
				jmxMgmt.register("/a/b", mbean);
				times = 1;
				jmxMgmt.register("/x/y", mbean);
				times = 1;
				jmxMgmt.unregister("/a/b");
				times = 1;
				jmxMgmt.unregister("/x/y");
				times = 1;
				jmxMgmt.close();
				times = 2;
			}
		};

		// do the same in separated threads
		ExecutorService threadPool = Executors.newFixedThreadPool(50);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			final String path = "/a/" + i;
			futures.add(threadPool.submit(new Runnable() {

				@Override
				public void run() {
					Management mgmt = mf.createManagement();
					// open the returned service
					try {
						mgmt.open();
						mgmt.register(path, mbean);
						mgmt.unregister(path);
						mgmt.close();
					} catch (ManagementException e) {
						Assert.fail();
					}
				}
			}));
		}
		for (Future<?> future : futures) {
			future.get(3000, TimeUnit.MILLISECONDS);
		}
		threadPool.shutdown();
	}
}
