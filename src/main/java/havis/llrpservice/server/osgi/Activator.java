package havis.llrpservice.server.osgi;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import havis.device.io.IODevice;
import havis.device.rf.RFDevice;
import havis.llrpservice.sbc.service.OSGiServiceFactory;
import havis.llrpservice.server.service.LLRPServiceManager;
import havis.util.platform.Platform;

/**
 * The OSGi activator starts the LLRP service and provides system controllers
 * and RF controllers. The controllers are searched with OSGi filters
 * <code>(&amp;(objectClass=havis.util.platform.Platform))</code>,
 * <code>(&(objectClass=havis.device.rf.RFDevice))</code> and
 * <code>(&(objectClass=havis.device.io.IODevice))</code>.
 * <p>
 * The required properties are configured in <code>bundle.properties</code>
 * file:
 * <ul>
 * <li><code>havis.llrpservice.config.base.path</code>: the path to the
 * configuration files of the LLRP service</li>
 * </ul>
 * </p>
 * <p>
 * If the file <code>bundle.properties</code> does not exist then the bundle
 * properties provided by the OSGi container are used.
 */
public class Activator implements BundleActivator {

	private static final Logger log = Logger.getLogger(Activator.class.getName());

	private static final String BUNDLE_PROP_FILE = "bundle.properties";
	private static final String BUNDLE_PROP_PREFIX = "havis.llrpservice.";
	private static final String PROP_CONFIG_BASE_PATH = "config.base.path";

	private LLRPServiceManager llrpServiceManager;
	private ExecutorService threadPool;
	private Future<?> llrpServiceManagerFuture;

	@Override
	public void start(final BundleContext bundleContext) throws Exception {
		// load bundle properties file
		Properties bundleProps = null;
		URL propFileURL = bundleContext.getBundle().getResource(BUNDLE_PROP_FILE);
		if (propFileURL != null) {
			bundleProps = new Properties();
			try (InputStream propStream = propFileURL.openStream()) {
				bundleProps.load(propStream);
				if (log.isLoggable(Level.INFO)) {
					log.log(Level.INFO, "Loaded bundle properties from file " + propFileURL);
				}
			}
		}

		// get properties
		String configBasePath = getBundleProperty(bundleProps, bundleContext,
				PROP_CONFIG_BASE_PATH);
		// create and start the LLRP service
		OSGiServiceFactory<Platform> platformServiceFactory = new OSGiServiceFactory<>(
				bundleContext, Platform.class);

		OSGiServiceFactory<RFDevice> rfcServiceFactory = new OSGiServiceFactory<>(bundleContext,
				RFDevice.class);

		OSGiServiceFactory<IODevice> gpioServiceFactory = new OSGiServiceFactory<>(bundleContext,
				IODevice.class);

		llrpServiceManager = new LLRPServiceManager(
				adjust2env(Paths.get(configBasePath)).toString(), platformServiceFactory,
				rfcServiceFactory, gpioServiceFactory);
		threadPool = Executors.newFixedThreadPool(1);
		llrpServiceManagerFuture = threadPool.submit(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				llrpServiceManager.run();
				return null;
			}
		});
	}

	@Override
	public void stop(BundleContext ctx) throws Exception {
		// stop the LLRP service
		if (llrpServiceManagerFuture != null) {
			llrpServiceManager.stop();
			llrpServiceManagerFuture.get();
			threadPool.shutdown();
		}
	}

	/**
	 * Converts a path from the bundle properties to an absolute path using the
	 * working directory of the environment as base path. It is necessary in an
	 * OSGi container to get resources outside of an OSGi bundle.
	 * <p>
	 * For testing purposes this method must be overwritten. Paths from bundle
	 * properties can be used directly.
	 * </p>
	 * 
	 * @param path
	 *            path from a bundle property
	 * @return The path
	 */
	Path adjust2env(Path path) {
		return path;
		// return path.toAbsolutePath();
	}

	/**
	 * Gets a bundle property from <code>bundle.properties</code> file. If the
	 * file does not contain the property then the property is read via the
	 * bundle context.
	 * 
	 * @param bundleProps
	 * @param bundleContext
	 * @param key
	 * @return The bundle property value
	 * @throws MissingPropertyException
	 */
	private String getBundleProperty(Properties bundleProps, BundleContext bundleContext,
			String key) throws MissingPropertyException {
		String value = null;
		if (bundleProps != null) {
			value = bundleProps.getProperty(BUNDLE_PROP_PREFIX + key);
		}
		if (value == null || value.trim().length() == 0) {
			value = bundleContext.getProperty(BUNDLE_PROP_PREFIX + key);
		}
		if (value == null || value.trim().length() == 0) {
			throw new MissingPropertyException(
					"Missing bundle property '" + BUNDLE_PROP_PREFIX + key + "'");
		}
		return value.trim();
	}
}
