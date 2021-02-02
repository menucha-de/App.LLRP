package havis.llrpservice.server.configuration;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityGroup;
import havis.llrpservice.common.entityManager.StaleEntityStateException;
import havis.llrpservice.common.entityManager.UnknownEntityException;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.common.serializer.XMLSerializer;
import havis.llrpservice.server.persistence.Persistence;
import havis.llrpservice.server.persistence._FileHelperTest;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;

import java.nio.file.Path;
import java.util.List;

import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ServerConfigurationTest {
	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/configuration/serverConfiguration");
	private static final Path INIT_PATH = BASE_PATH
			.resolve("LLRPServerConfiguration.xml");

	private static final Path BASE_OUTPUT_PATH = BASE_PATH
			.resolve("../../../../../../output");
	private static final Path LATEST_PATH = BASE_OUTPUT_PATH
			.resolve("config/newConfig.xml");
	private static final Path REPOSITORY_PATH = BASE_OUTPUT_PATH
			.resolve("config/_repository_");

	private ServerConfiguration testConfig = null;

	private class ServerConfigurationTestListener implements
			ServerConfigurationListener {
		private boolean updatedCalled = false;

		@Override
		public void updated(ServerConfiguration config) {
			updatedCalled = true;
		}

	}

	@BeforeClass
	public void init() throws UnknownEntityException, Exception {
		cleanUp();

		XMLFile<LLRPServerConfigurationType> configFile = new XMLFile<>(
				LLRPServerConfigurationType.class, INIT_PATH, LATEST_PATH);
		testConfig = new ServerConfiguration(configFile);
		testConfig.open();
	}

	@Test
	public void open() throws UnknownEntityException, Exception {
		// Create config file
		XMLFile<LLRPServerConfigurationType> configFile = new XMLFile<>(
				LLRPServerConfigurationType.class, INIT_PATH, LATEST_PATH);
		// Create ServerConfiguration
		ServerConfiguration config = new ServerConfiguration(configFile);
		// Call open
		config.open();
		// Create serializier to evaluate ServerConfiguration objects
		XMLSerializer<LLRPServerConfigurationType> serializer = new XMLSerializer<>(
				LLRPServerConfigurationType.class);
		// Activate pretty print
		serializer.setPrettyPrint(true);
		// Serialize config object (from file)
		LLRPServerConfigurationType instance1 = configFile.getContent();
		String expected = serializer.serialize(instance1);

		// Serialize config object (from configuration object)
		Entity<LLRPServerConfigurationType> entity = config.acquire();
		String result = serializer.serialize(entity.getObject());
		// Flush object (autoflush)
		config.release(entity, /* write */true);
		// Configurations equal
		Assert.assertEquals(result, expected);

		// get persistence from object
		Persistence persistence = config.getPersistence();

		// get groups
		List<EntityGroup> groups = persistence
				.getGroups(LLRPServerConfigurationType.class);
		// Refresh from storage to get entityID of flushed object
		List<String> entityIds = persistence.refresh(
				LLRPServerConfigurationType.class, groups.get(0).getGroupId());
		// Acquire object from persistence
		List<Entity<Object>> LLRPconfList = persistence.acquire(entityIds);
		persistence.release(LLRPconfList, false);
		// Configurations equal
		LLRPServerConfigurationType instance3 = (LLRPServerConfigurationType) LLRPconfList
				.get(0).getObject();
		result = serializer.serialize(instance3);
		Assert.assertEquals(result, expected);

		// Read latest file from config storage
		String latest = _FileHelperTest.readFile(LATEST_PATH.toString());

		// Read flushed object from storage
		String repfile = _FileHelperTest.readFile(REPOSITORY_PATH.resolve(
				entityIds.get(0)).toString());

		// Are equal
		Assert.assertEquals(latest, repfile);

		// Instances should not be equal
		Assert.assertNotEquals(instance1, entity.getObject());
		Assert.assertNotEquals(instance1, instance3);
		Assert.assertNotEquals(entity.getObject(), instance3);

		config.close();
		cleanUp();
	}

	@Test
	public void listeners() throws UnknownEntityException, Exception {
		// Create config file
		XMLFile<LLRPServerConfigurationType> configFile = new XMLFile<>(
				LLRPServerConfigurationType.class, INIT_PATH, LATEST_PATH);
		// Create ServerConfiguration
		ServerConfiguration config = new ServerConfiguration(configFile);
		// Call open
		config.open();

		// Create a listener and add it to ServerConfiguration
		ServerConfigurationTestListener listener = new ServerConfigurationTestListener();
		config.addListener(listener);

		// Prevail ServerConfiguration to fire the update event
		Entity<LLRPServerConfigurationType> entity = config.acquire();
		config.release(entity, /* write */true);

		// Updated called
		Assert.assertEquals(listener.updatedCalled, true);
		listener.updatedCalled = false;
		config.removeListener(listener);

		// Prevail ServerConfiguration to fire the update event
		entity = config.acquire();
		config.release(entity, /* write */true);

		// Udated should not be called
		Assert.assertEquals(listener.updatedCalled, false);
		config.close();
		cleanUp();
	}

	@Test
	public void exceptions(
			@Mocked final XMLFile<LLRPServerConfigurationType> configFile)
			throws Exception {
		new NonStrictExpectations() {
			{
				configFile.getContent();
				result = new ConfigurationException(new Exception(
						"Any Exception"));
			}
		};
		ServerConfiguration config = new ServerConfiguration(configFile);
		try {
			config.open();
			Assert.fail();
		} catch (ConfigurationException e) {
			Assert.assertTrue(e.getCause().getMessage()
					.contains("Any Exception"));
		}

	}

	@Test(invocationCount = 100, threadPoolSize = 1)
	public void threads() throws UnknownEntityException, Exception {
		// Add a listener
		ServerConfigurationListener listener = new ServerConfigurationTestListener();
		testConfig.addListener(listener);

		// Prevail ServerConfiguration to fire the update event
		Entity<LLRPServerConfigurationType> entity = testConfig.acquire();
		try {
			testConfig.release(entity, /* write */true);
		} catch (StaleEntityStateException e) {
			// Expected exception, can be thrown
			Assert.assertTrue(e
					.getMessage()
					.contains(
							"cannot be replaced because the entity was already changed otherwise"));
		} catch (Exception e) {
			e.printStackTrace();
			// Unexpected exception
			Assert.fail();
		}
		testConfig.removeListener(listener);
	}

	@AfterClass
	public void cleanUp() {
		try {
			if (testConfig != null) {
				testConfig.close();
			}

			// remove output directory
			_FileHelperTest.deleteFiles(BASE_OUTPUT_PATH.toString());
			BASE_OUTPUT_PATH.toFile().delete();
		} catch (Exception e) {
		}
	}

}
