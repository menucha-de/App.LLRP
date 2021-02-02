package havis.llrpservice.server.platform;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.sbc.service.ReflectionServiceFactory;
import havis.llrpservice.sbc.service.ServiceFactory;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.configuration.ServerConfiguration;
import havis.llrpservice.server.configuration.ServerInstanceConfiguration;
import havis.llrpservice.server.persistence.PersistenceException;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.ReflectionType;
import havis.llrpservice.xml.configuration.SystemControllerPortProperties;
import havis.util.platform.Platform;

/**
 * The PlatformManager manages the getting and releasing of platform
 * controllers. The LLRP configuration defines which service factory for
 * platform controllers has to be used.
 */
public class PlatformManager {
	private ServiceFactory<Platform> serviceFactory;
	private final String host;
	private final int port;
	private int openCloseTimeout;

	/**
	 * @param serverConfiguration
	 * @param instanceConfiguration
	 * @param serviceFactory
	 *            The service factory for platform controllers provided by the
	 *            OSGi platform. If it is <code>null</code> then the LLRP
	 *            configuration must provide the properties for creating a
	 *            platform controller via the Java Reflection API.
	 * 
	 * @throws EntityManagerException
	 * @throws ConfigurationException
	 * @throws PersistenceException
	 * @throws ClassNotFoundException
	 *             the creating of a platform controller via Java Reflection API
	 *             failed
	 * @throws MissingServiceFactoryException
	 * 
	 */
	public PlatformManager(ServerConfiguration serverConfiguration,
			ServerInstanceConfiguration instanceConfiguration,
			ServiceFactory<Platform> serviceFactory)
			throws EntityManagerException, ConfigurationException, PersistenceException,
			ClassNotFoundException, MissingServiceFactoryException {
		// create config analyser
		Entity<LLRPServerConfigurationType> serverConfigEntity = serverConfiguration.acquire();
		Entity<LLRPServerInstanceConfigurationType> instanceConfigEntity = instanceConfiguration
				.acquire();
		PlatformConfigAnalyser configAnalyser = new PlatformConfigAnalyser(
				serverConfigEntity.getObject());
		configAnalyser.setServerInstanceConfig(instanceConfigEntity.getObject());
		instanceConfiguration.release(instanceConfigEntity, false /* write */);
		serverConfiguration.release(serverConfigEntity, false /* write */);

		host = configAnalyser.getAddress().getHost();
		port = configAnalyser.getAddress().getPort();
		SystemControllerPortProperties platformPortProperties = configAnalyser
				.getSystemControllerPortProperties();
		openCloseTimeout = platformPortProperties.getOpenCloseTimeout();
		// if reflection is activated
		if (platformPortProperties.ifReflection()) {
			ReflectionType reflectionProps = platformPortProperties.getReflection();
			String addressSetterMethodName = reflectionProps.getAddressSetterMethodName();
			if (addressSetterMethodName != null) {
				addressSetterMethodName = addressSetterMethodName.trim();
			}
			// replace an existing service factory
			serviceFactory = new ReflectionServiceFactory<>(
					reflectionProps.getControllerClassName().trim(), addressSetterMethodName);

		} // else if OSGi is activated and no service factory exists
		else if (platformPortProperties.ifOSGi() && serviceFactory == null) {
			throw new MissingServiceFactoryException("Missing OSGi service factory");
		}
		this.serviceFactory = serviceFactory;
	}

	/**
	 * Gets a platform controller. The platform controller may be cached
	 * internally and returned multiple times. It must be released with
	 * reference for each call of this method.
	 * 
	 * @return The platform controller
	 * @throws Exception
	 */
	public Platform getService() throws Exception {
		return serviceFactory.getService(host, port, openCloseTimeout);
	}

	/**
	 * Releases a platform controller which has been returned with
	 * {@link #getService()}. An opened controller must be closed by
	 * {@link Platform#close()} before.
	 * 
	 * @param platformController
	 * @throws Exception
	 */
	public void release(Platform platformController) throws Exception {
		serviceFactory.release(platformController);
	}
}
