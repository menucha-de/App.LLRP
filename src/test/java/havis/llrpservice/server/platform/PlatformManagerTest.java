package havis.llrpservice.server.platform;

import org.testng.Assert;
import org.testng.annotations.Test;

import havis.llrpservice.sbc.service.OSGiServiceFactory;
import havis.llrpservice.sbc.service.ReflectionServiceFactory;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.platform.MissingServiceFactoryException;
import havis.llrpservice.server.platform.PlatformConfigAnalyser;
import havis.llrpservice.server.platform.PlatformManager;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.util.platform.Platform;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

public class PlatformManagerTest {

	@Test
	public void getServiceByReflection(@Mocked final ServerConfiguration serverConfiguration,
			@Mocked final LLRPServerConfigurationType serverConfigType,
			@Mocked final ServerInstanceConfiguration instanceConfiguration,
			@Mocked final LLRPServerInstanceConfigurationType instanceConfigType,
			@Mocked final PlatformConfigAnalyser confAnalyser,
			@Mocked final OSGiServiceFactory<Platform> osgiServiceFactory,
			@Mocked final ReflectionServiceFactory<Platform> rServiceFactory,
			@Mocked final Platform systemController) throws Exception {
		// create a config with enabled reflection
		new NonStrictExpectations() {
			{
				serverConfiguration.acquire().getObject();
				result = serverConfigType;

				instanceConfiguration.acquire().getObject();
				result = instanceConfigType;

				confAnalyser.getAddress().getHost();
				result = "host";
				confAnalyser.getAddress().getPort();
				result = 3;
				confAnalyser.getSystemControllerPortProperties().getOpenCloseTimeout();
				result = 4;
				confAnalyser.getSystemControllerPortProperties().ifReflection();
				result = true;
				confAnalyser.getSystemControllerPortProperties().ifOSGi();
				result = true;
				confAnalyser.getSystemControllerPortProperties().getReflection()
						.getAddressSetterMethodName();
				result = "addressSetterMethodName";
				confAnalyser.getSystemControllerPortProperties().getReflection()
						.getControllerClassName();
				result = "controllerClassName";

				rServiceFactory.getService("host", 3 /* port */, 4 /* openCloseTimeout */);
				result = systemController;
			}
		};

		// create a manager with an OSGiServiceFactory
		PlatformManager scm = new PlatformManager(serverConfiguration,
				instanceConfiguration, osgiServiceFactory);
		// get a service
		final Platform service = scm.getService();
		// the service created by the ReflectionServiceFactory is returned
		Assert.assertEquals(service, systemController);

		// release the service
		scm.release(service);

		new Verifications() {
			{
				// the properties for reflection are used
				new ReflectionServiceFactory<>("controllerClassName", "addressSetterMethodName");
				times = 1;

				// the service is released at the ReflectionServiceFactory
				rServiceFactory.release(service);
				times = 1;
			}
		};
	}

	@Test
	public void getServiceByOSGi(@Mocked final ServerConfiguration serverConfiguration,
			@Mocked final LLRPServerConfigurationType serverConfigType,
			@Mocked final ServerInstanceConfiguration instanceConfiguration,
			@Mocked final LLRPServerInstanceConfigurationType instanceConfigType,
			@Mocked final PlatformConfigAnalyser confAnalyser,
			@Mocked final OSGiServiceFactory<Platform> osgiServiceFactory,
			@Mocked final Platform systemController) throws Exception {
		// create a config with enabled OSGi
		new NonStrictExpectations() {
			{
				serverConfiguration.acquire().getObject();
				result = serverConfigType;

				instanceConfiguration.acquire().getObject();
				result = instanceConfigType;

				confAnalyser.getAddress().getHost();
				result = "host";
				confAnalyser.getAddress().getPort();
				result = 3;
				confAnalyser.getSystemControllerPortProperties().getOpenCloseTimeout();
				result = 4;
				confAnalyser.getSystemControllerPortProperties().ifOSGi();
				result = true;

				osgiServiceFactory.getService("host", 3 /* port */, 4 /* openCloseTimeout */);
				result = systemController;
			}
		};

		// create a manager with an OSGiServiceFactory
		PlatformManager scm = new PlatformManager(serverConfiguration,
				instanceConfiguration, osgiServiceFactory);
		// get a service
		final Platform service = scm.getService();
		// the service created by the OSGiServiceFactory is returned
		Assert.assertEquals(service, systemController);

		// release the service
		scm.release(service);

		new Verifications() {
			{
				// the service is released at the OSGiServiceFactory
				osgiServiceFactory.release(service);
				times = 1;
			}
		};
	}

	@Test
	public void getServiceError(@Mocked final ServerConfiguration serverConfiguration,
			@Mocked final LLRPServerConfigurationType serverConfigType,
			@Mocked final ServerInstanceConfiguration instanceConfiguration,
			@Mocked final LLRPServerInstanceConfigurationType instanceConfigType,
			@Mocked final PlatformConfigAnalyser confAnalyser) throws Exception {
		// create a config with enabled OSGi
		new NonStrictExpectations() {
			{
				serverConfiguration.acquire().getObject();
				result = serverConfigType;

				instanceConfiguration.acquire().getObject();
				result = instanceConfigType;

				confAnalyser.getSystemControllerPortProperties().ifOSGi();
				result = true;
			}
		};

		// try to create a manager without an OSGiServiceFactory
		try {
			new PlatformManager(serverConfiguration, instanceConfiguration,
					null /* osgiServiceFactory */);
		} catch (MissingServiceFactoryException e) {
			Assert.assertTrue(e.getMessage().contains("Missing OSGi service factory"));
		}
	}
}
