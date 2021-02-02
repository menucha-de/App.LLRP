package havis.llrpservice.server.osgi;

import havis.device.io.IODevice;
import havis.device.rf.RFDevice;
import havis.llrpservice.sbc.service.OSGiServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.server.service.LLRPServiceManager;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ActivatorTest {
	private Path BASE_RESOURCE_PATH = Paths.get(ActivatorTest.class
			.getPackage().getName().replace('.', '/'));

	@SuppressWarnings("unchecked")
	@Test
	public void start(@Mocked final BundleContext bundleContext,
			@Mocked final Logger log,
			@Mocked final LLRPServiceManager llrpServiceManager)
			throws Exception {
		class Data {
			URL url;
			String path;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				bundleContext.getBundle().getResource("bundle.properties");
				result = new Delegate<Bundle>() {
					@SuppressWarnings("unused")
					URL getResource(String name) {
						return data.url;
					}
				};

				bundleContext.getProperty("havis.llrpservice.config.base.path");
				result = new Delegate<BundleContext>() {
					@SuppressWarnings("unused")
					String getProperty(String key) {
						return data.path;
					}
				};

				log.isLoggable(Level.INFO);
				result = true;
			}
		};
		Logger origLog = Deencapsulation.getField(Activator.class, "log");
		Deencapsulation.setField(Activator.class, "log", log);

		// start the bundle with the required property
		// "havis.llrpservice.config.base.path" at both possible locations
		Activator activator = new Activator() {
			@Override
			Path adjust2env(Path path) {
				// directly use the the relative path starting at the class path
				return path;
			}
		};
		data.url = getClass().getClassLoader().getResource(
				BASE_RESOURCE_PATH.resolve("bundle.properties").toString());
		data.path = "huhu";
		activator.start(bundleContext);

		new Verifications() {
			{
				// the LLRP service manager is created with the config base path
				// from bundle.properties
				String path;
				ServiceFactory<RFDevice> rfcServiceFactory;
				ServiceFactory<IODevice> gpioServiceFactory;
				new LLRPServiceManager(path = withCapture(),
						withInstanceOf(OSGiServiceFactory.class),
						rfcServiceFactory = withCapture(),
						gpioServiceFactory = withCapture());
				times = 1;
				Assert.assertEquals(path, "conf/havis-llrpservice");
				Assert.assertNotNull(rfcServiceFactory);
				Assert.assertNotNull(gpioServiceFactory);

				// the LLRP service is started and stopped
				llrpServiceManager.run();
				times = 1;
			}
		};

		// stop the bundle
		activator.stop(bundleContext);

		new Verifications() {
			{
				// the LLRP service is stopped
				llrpServiceManager.stop();
				times = 1;
			}
		};
		
		Deencapsulation.setField(Activator.class, "log", origLog);
	}

	@Test
	public void startError(@Mocked final BundleContext bundleContext,
			@Mocked LLRPServiceManager llrpServerManager) throws Exception {
		class Data {
			URL url;
			String path;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				bundleContext.getBundle().getResource("bundle.properties");
				result = new Delegate<Bundle>() {
					@SuppressWarnings("unused")
					URL getResource(String name) {
						return data.url;
					}
				};

				bundleContext.getProperty("havis.llrpservice.config.base.path");
				result = new Delegate<BundleContext>() {
					@SuppressWarnings("unused")
					String getProperty(String key) {
						return data.path;
					}
				};
			}
		};

		// start the bundle without the required property
		// "havis.llrpservice.config.base.path"
		Activator activator = new Activator() {
			@Override
			Path adjust2env(Path path) {
				// directly use the the relative path starting at the class path
				return path;
			}
		};
		data.url = getClass().getClassLoader().getResource(
				BASE_RESOURCE_PATH.resolve("bundleMissingProp.properties")
						.toString());
		try {
			activator.start(bundleContext);
			Assert.fail();
		} catch (MissingPropertyException e) {
			Assert.assertTrue(e.getMessage().contains(
					"havis.llrpservice.config.base.path"));
		}

		// set the config.base.path to the properties provided by the OSGi
		// container
		data.path = "huhu";
		activator.start(bundleContext);
	}
}
