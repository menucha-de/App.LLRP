package havis.llrpservice.server.configuration;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityGroup;
import havis.llrpservice.common.entityManager.UnknownEntityException;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.common.serializer.XMLSerializer;
import havis.llrpservice.server.persistence.Persistence;
import havis.llrpservice.server.persistence._FileHelperTest;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

import java.nio.file.Path;
import java.util.List;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

public class ServerInstanceConfigurationTest {

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/configuration/serverInstanceConfiguration");
	private static final Path SERVER_INIT_PATH = BASE_PATH
			.resolve("LLRPServerConfiguration.xml");
	private static final Path INIT_PATH = BASE_PATH
			.resolve("LLRPServerInstanceConfiguration.xml");

	private static final Path BASE_OUTPUT_PATH = BASE_PATH
			.resolve("../../../../../../output");
	private static final Path SERVER_LATEST_PATH = BASE_OUTPUT_PATH
			.resolve("config/newConfig.xml");
	private static final Path LATEST_PATH = BASE_OUTPUT_PATH
			.resolve("config/newInstanceConfig.xml");
	private static final Path REPOSITORY_PATH = BASE_OUTPUT_PATH
			.resolve("configInstance/_repository_");

	private class ServerConfigurationTestListener implements
			ServerConfigurationListener {

		private boolean updatedCalled = false;

		@Override
		public void updated(ServerConfiguration config) {
			updatedCalled = true;
		}

	}

	private class ServerInstanceConfigurationTestListener implements
			ServerInstanceConfigurationListener {

		private boolean updatedCalled = false;
		private boolean updateException = false;

		@Override
		public void updated(ServerInstanceConfiguration src,
				ServerConfiguration serverConf, Throwable exception) {
			updatedCalled = true;
			if (exception != null) {
				updateException = true;
			}
		}

	}

	@Test
	public void open() throws UnknownEntityException, Exception {
		// Create ServerConfigFile
		XMLFile<LLRPServerConfigurationType> serverConfigFile = new XMLFile<>(
				LLRPServerConfigurationType.class, SERVER_INIT_PATH,
				SERVER_LATEST_PATH);
		// Create ServerConfiguration
		ServerConfiguration serverConf = new ServerConfiguration(
				serverConfigFile);
		serverConf.open();

		// Create ServerInstanceConfiguration file
		XMLFile<LLRPServerInstanceConfigurationType> instanceConfigFile = new XMLFile<>(
				LLRPServerInstanceConfigurationType.class, INIT_PATH,
				LATEST_PATH);

		// create ServerInstanceConfiguration
		ServerInstanceConfiguration instanceConf = new ServerInstanceConfiguration(
				serverConf, instanceConfigFile);
		instanceConf.open();

		// Create serializier to evaluate ServerInstanceConfiguration objects
		XMLSerializer<LLRPServerInstanceConfigurationType> serializer = new XMLSerializer<>(
				LLRPServerInstanceConfigurationType.class);
		// Activate pretty print
		serializer.setPrettyPrint(true);

		// Serialize config object (from file)
		LLRPServerInstanceConfigurationType instance1 = instanceConfigFile
				.getContent();
		String expected = serializer.serialize(instance1);

		// Serialize config object (from configuration object)
		Entity<LLRPServerInstanceConfigurationType> entity = instanceConf
				.acquire();
		String result = serializer.serialize(entity.getObject());

		// Flush object (autoflush)
		instanceConf.release(entity, /* write */true);

		// Configurations equal
		Assert.assertEquals(result, expected);

		// get persistence from object
		Persistence persistence = instanceConf.getPersistence();

		// get groups
		List<EntityGroup> groups = persistence
				.getGroups(LLRPServerInstanceConfigurationType.class);
		// Refresh from storage to get entityID of flushed object
		List<String> entityIds = persistence.refresh(
				LLRPServerInstanceConfigurationType.class, groups.get(0)
						.getGroupId());
		// Acquire object from persistence
		List<Entity<Object>> LLRPconfList = persistence.acquire(entityIds);
		persistence.release(LLRPconfList, false);
		// Configurations equal
		LLRPServerInstanceConfigurationType instance3 = (LLRPServerInstanceConfigurationType) LLRPconfList
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

		instanceConf.close();
		cleanUp();

	}

	@Test
	public void listeners() throws UnknownEntityException, Exception {
		// Create ServerConfigFile
		XMLFile<LLRPServerConfigurationType> serverConfigFile = new XMLFile<>(
				LLRPServerConfigurationType.class, SERVER_INIT_PATH,
				SERVER_LATEST_PATH);
		// Create ServerConfiguration
		ServerConfigurationTestListener serverListener = new ServerConfigurationTestListener();
		ServerConfiguration serverConf = new ServerConfiguration(
				serverConfigFile);
		serverConf.open();
		serverConf.addListener(serverListener);

		// Prevail ServerConfiguration to fire the update event
		Entity<LLRPServerConfigurationType> serverEntity = serverConf.acquire();
		serverConf.release(serverEntity, /* write */true);

		// Updated called
		Assert.assertEquals(serverListener.updatedCalled, true);
		serverListener.updatedCalled = false;
		serverConf.removeListener(serverListener);

		// Create ServerInstanceConfiguration file
		XMLFile<LLRPServerInstanceConfigurationType> instanceConfigFile = new XMLFile<>(
				LLRPServerInstanceConfigurationType.class, INIT_PATH,
				LATEST_PATH);

		// create ServerInstanceConfiguration
		ServerInstanceConfiguration instanceConf = new ServerInstanceConfiguration(
				serverConf, instanceConfigFile);

		instanceConf.open();

		ServerInstanceConfigurationTestListener listener = new ServerInstanceConfigurationTestListener();
		instanceConf.addListener(listener);

		// Prevail ServerInstanceConfiguration to fire the update event
		Entity<LLRPServerInstanceConfigurationType> instanceEntity = instanceConf
				.acquire();
		instanceConf.release(instanceEntity, /* write */true);

		// Prevail ServerConfiguration to fire the update event
		serverEntity = serverConf.acquire();
		serverConf.release(serverEntity, /* write */true);

		// Updated called
		Assert.assertEquals(listener.updatedCalled, true);
		listener.updatedCalled = false;
		instanceConf.removeListener(listener);

		// Prevail ServerInstanceConfiguration to fire the update event
		instanceEntity = instanceConf.acquire();
		instanceConf.release(instanceEntity, /* write */true);

		// Udated should not be called
		Assert.assertEquals(listener.updatedCalled, false);

		instanceConf.close();

		cleanUp();

	}

	@Test
	public void testUpdatedMoc() throws Exception {
		new MockUp<ServerConfiguration>() {
			int counter = 0;

			@SuppressWarnings("unused")
			@Mock
			void release(Invocation inv,
					Entity<LLRPServerConfigurationType> entity, boolean write)
					throws Exception {
				counter++;
				// Exception must be thrown at third call (in update
				// method)
				if (counter < 3) {
					inv.proceed();
				} else {
					throw new Exception("Hallo");
				}
			}
		};

		// Create ServerConfigFile
		XMLFile<LLRPServerConfigurationType> serverConfigFile = new XMLFile<>(
				LLRPServerConfigurationType.class, SERVER_INIT_PATH,
				SERVER_LATEST_PATH);
		// Create ServerConfiguration
		ServerConfiguration serverConf = new ServerConfiguration(
				serverConfigFile);
		serverConf.open();

		// Create ServerInstanceConfiguration file
		XMLFile<LLRPServerInstanceConfigurationType> instanceConfigFile = new XMLFile<>(
				LLRPServerInstanceConfigurationType.class, INIT_PATH,
				LATEST_PATH);

		// create ServerInstanceConfiguration
		ServerInstanceConfiguration instanceConf = new ServerInstanceConfiguration(
				serverConf, instanceConfigFile);
		instanceConf.open();

		ServerInstanceConfigurationTestListener listener = new ServerInstanceConfigurationTestListener();
		instanceConf.addListener(listener);

		// Prevail ServerInstanceConfiguration to fire the update event
		Entity<LLRPServerConfigurationType> entity = serverConf.acquire();
		serverConf.release(entity, true);

		Assert.assertTrue(listener.updateException);

		instanceConf.close();

	}

	@AfterClass
	public void cleanUp() {
		// Remove output directory
		try {
			_FileHelperTest.deleteFiles(BASE_OUTPUT_PATH.toString());
			BASE_OUTPUT_PATH.toFile().delete();
		} catch (Exception e) {
		}
	}

}
