package havis.llrpservice.server.persistence;

import havis.llrpservice.common.entityManager.Entity;
import havis.llrpservice.common.entityManager.EntityGroup;
import havis.llrpservice.common.entityManager.EntityManagerException;
import havis.llrpservice.common.entityManager.FileEntityManager;
import havis.llrpservice.common.entityManager.InMemoryEntityManager;
import havis.llrpservice.common.entityManager.JSONFileEntityManager;
import havis.llrpservice.common.entityManager.UnknownEntityException;
import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.serializer.XMLSerializer;
import havis.llrpservice.server.configuration.ConfigurationException;
import havis.llrpservice.server.persistence._TestClassTest.Enumeration;
import havis.llrpservice.xml.configuration.EntityType;
import havis.llrpservice.xml.configuration.JSONType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test
public class PersistenceTest {

	private final static String serverConfigFile = "/havis/llrpservice/server/persistence/persistence/LLRPServerConfiguration.xml";
	private final static String instanceConfigFile = "/havis/llrpservice/server/persistence/persistence/LLRPServerInstanceConfiguration.xml";
	private String serverConfigPath;
	private String instanceConfigPath;

	// Output Directory
	private String outputDir = "../../../../../../output/";

	private Persistence persistence;
	LLRPServerConfigurationType serverConfig;
	LLRPServerInstanceConfigurationType serverInstanceConfig;

	@BeforeMethod
	public void init() throws UnknownEntityException, Exception {
		// Cleanup
		try {
			_FileHelperTest.deleteFiles(outputDir);
			new File(outputDir).delete();
		} catch (Exception e) {
		}

		// read server config
		InputStream is = getClass().getResourceAsStream(serverConfigFile);
		String content = _FileHelperTest.readFile(is);
		serverConfig = new XMLSerializer<>(LLRPServerConfigurationType.class)
				.deserialize(content);

		// read server instance config
		is = getClass().getResourceAsStream(instanceConfigFile);
		content = _FileHelperTest.readFile(is);
		serverInstanceConfig = new XMLSerializer<>(
				LLRPServerInstanceConfigurationType.class).deserialize(content);

		Path absPath = new PathHandler().toAbsolutePath(serverConfigFile
				.substring(1));
		serverConfigPath = absPath.getParent().toString();

		outputDir = absPath.getParent().resolve(outputDir).toString();

		instanceConfigPath = new PathHandler()
				.toAbsolutePath(instanceConfigFile.substring(1)).getParent()
				.toString();
		persistence = new Persistence();
		persistence.setServerConfiguration(serverConfig, serverInstanceConfig,
				serverConfigPath, instanceConfigPath);
		persistence.addClass(String.class, "1.0");
		persistence.addClass(_TestClassTest.class, "1.0");
		persistence.addClass(LLRPServerConfigurationType.class, "1.0");
		persistence.addClass(Integer.class, "1.0");
		persistence.addClass(Long.class, "1.0");
	}

	public void openClose(@Mocked final FileEntityManager<?> fileEntityManager)
			throws Exception {
		final InMemoryEntityManager<?> inMemEntityManager = new InMemoryEntityManager<>();
		new NonStrictExpectations(InMemoryEntityManager.class) {
			{
				inMemEntityManager.open();
				inMemEntityManager.close();
			}
		};
		// open the persistence
		persistence.open();
		// existing entity managers are opened
		new Verifications() {
			{
				inMemEntityManager.open();
				times = 2; // String, Long
				fileEntityManager.open();
				times = 3; // LLRPServerConfigurationType, _TestClassTest,
							// Integer
			}
		};

		// set new config
		persistence.setServerConfiguration(serverConfig, serverInstanceConfig,
				serverConfigPath, instanceConfigPath);
		// the current entity managers are closed and new ones are opened
		new Verifications() {
			{
				inMemEntityManager.close();
				times = 2;
				fileEntityManager.close();
				times = 3;

				inMemEntityManager.open();
				times = 4;
				fileEntityManager.open();
				times = 6;
			}
		};

		// close the persistence
		persistence.close();
		// the entity managers are closed
		new Verifications() {
			{
				inMemEntityManager.open();
				times = 4;
				fileEntityManager.open();
				times = 6;

				inMemEntityManager.close();
				times = 4;
				fileEntityManager.close();
				times = 6;
			}
		};
	}

	public void setConfiguration() throws Exception {
		persistence.addClass(LLRPServerInstanceConfigurationType.class, "1.0");
		persistence.open();

		// only set the server config
		persistence.setServerConfiguration(serverConfig, serverConfigPath);
		// add a server instance config
		List<String> entityIds = persistence
				.add(LLRPServerInstanceConfigurationType.class,
						Arrays.asList(new LLRPServerInstanceConfigurationType[] { serverInstanceConfig }));
		// no group has been created because auto flush is disabled for the
		// entity type in server config
		List<EntityGroup> groups = persistence
				.getGroups(LLRPServerInstanceConfigurationType.class);
		Assert.assertEquals(groups.size(), 0);

		// add server instance configuration with an enabled auto flush for the
		// entity type
		persistence.setServerConfiguration(serverConfig, serverInstanceConfig,
				serverConfigPath, instanceConfigPath);
		// remove entity
		persistence.remove(entityIds);
		// a group has been created
		groups = persistence
				.getGroups(LLRPServerInstanceConfigurationType.class);
		Assert.assertEquals(groups.size(), 1);

		persistence.close();
		persistence.removeClass(LLRPServerInstanceConfigurationType.class);

		// Cleanup
		_FileHelperTest.deleteFiles(outputDir);
		new File(outputDir).delete();
	}

	public void jacksonMixIns() throws Exception {
		persistence.open();

		// add an entity for JSON serializer
		persistence.add(_TestClassTest.class, Arrays
				.asList(new _TestClassTest[] { new _TestClassTest(
						new _InnerTestClassTest("Foo", 1), "Max", 42,
						Enumeration.FIRST) }));
		// flush the entity
		String groupId = persistence.flush(_TestClassTest.class);
		// load entity with JSON serializer
		List<String> entityIds = persistence.refresh(_TestClassTest.class,
				groupId);
		// the entity has been loaded successfully
		List<Entity<Object>> entities = persistence.acquire(entityIds);
		_TestClassTest entity = (_TestClassTest) entities.get(0).getObject();
		Assert.assertEquals(entity.getAbc(), "Max");

		// remove class info for mixIns from config
		JSONType json = null;
		for (EntityType e : serverConfig.getDefaults().getPersistence()
				.getEntities().getEntityList()) {
			if (e.getClassName().contains("_TestClassTest")) {
				json = e.getOutput().getFile().getFileProperties().getType()
						.getJSON();
				break;
			}
		}
		json.setMixInClassName(null);
		persistence.setServerConfiguration(serverConfig, serverInstanceConfig,
				serverConfigPath, instanceConfigPath);

		// try to load the entity again
		try {
			persistence.refresh(_TestClassTest.class, groupId);
			Assert.fail();
		} catch (EntityManagerException e) {
			// the entity cannot be deserialized without mixIns because the
			// default constructor does not exist
			Assert.assertTrue(e.getCause().getMessage()
					.contains("No suitable constructor found"));
		} catch (Exception e) {
			Assert.fail();
		}

		json.setMixInClassName("invalidClass");
		// try to load the entity again
		try {
			persistence.setServerConfiguration(serverConfig,
					serverInstanceConfig, serverConfigPath, instanceConfigPath);
			Assert.fail();
		} catch (ConfigurationException e) {
			Assert.assertTrue(e.getCause().getMessage()
					.contains("invalidClass"));
		} catch (Exception e) {
			Assert.fail();
		}

		persistence.close();

		// reset persistence because of exception in setServerConfiguration
		init();
	}

	public void addRemoveClass(
			@Mocked final FileEntityManager<?> fileEntityManager)
			throws Exception {
		//final FileEntityManager<?> fileEntityManager = new JSONFileEntityManager<>(clazz, classVersion, fileProperties, jsonProperties)
		final InMemoryEntityManager<?> inMemEntityManager = new InMemoryEntityManager<>();
		new NonStrictExpectations(InMemoryEntityManager.class) {
			{
				inMemEntityManager.open();
				inMemEntityManager.close();
			}
		};
		// remove a class from a closed persistence
		persistence.removeClass(_TestClassTest.class);
		// the removed entity manager is NOT opened
		new Verifications() {
			{
				fileEntityManager.close();
				times = 0;
			}
		};

		// open the persistence
		persistence.open();
		new Verifications() {
			{
				fileEntityManager.open();
				times = 2; // LLRPServerConfigurationType, Integer
			}
		};

		// remove a class from the opened persistence
		persistence.removeClass(Integer.class);
		// the removed entity manager is closed
		new Verifications() {
			{
				fileEntityManager.close();
				times = 1;
			}
		};

		// add a class to the opened persistence
		persistence.addClass(Integer.class, "1.0");
		// the created entity manager is opened
		new Verifications() {
			{
				fileEntityManager.open();
				times = 3;
			}
		};

		// close the persistence
		persistence.close();
		new Verifications() {
			{
				fileEntityManager.close();
				times = 3; // LLRPServerConfigurationType, Integer
			}
		};

		// add a class to a closed persistence
		persistence.addClass(_TestClassTest.class, "1.0");
		// the created entity manager is NOT opened
		new Verifications() {
			{
				fileEntityManager.open();
				times = 3;
			}
		};

		// try to remove an unknown class
		try {
			persistence.removeClass(Double.class);
			Assert.fail();
		} catch (UnknownClassException e) {
			Assert.assertTrue(e.getMessage().contains("Unknown class"));
		}

		// try to remove an unknown entityId
		try {
			persistence.remove(Arrays.asList(new String[] { "huhu" }));
			Assert.fail();
		} catch (UnknownEntityException e) {
			Assert.assertTrue(e.getMessage().contains("unknown entities"));
		}

		// try to remove an unknown entityId
		try {
			persistence.refresh(Double.class, "groupId");
			Assert.fail();
		} catch (UnknownClassException e) {
			Assert.assertTrue(e.getMessage().contains("Unknown class"));
		}
	}

	public void addRemoveGetGroups() throws Exception {
		persistence.open();

		// add an entity (XML, auto flush)
		List<String> xmlEntityIds = persistence
				.add(LLRPServerConfigurationType.class,
						Arrays.asList(new LLRPServerConfigurationType[] { serverConfig }));
		// a group with the entity has been created
		List<EntityGroup> groups = persistence
				.getGroups(LLRPServerConfigurationType.class);

		Assert.assertTrue(new File(outputDir + "/config/"
				+ groups.get(0).getGroupId() + "/" + xmlEntityIds.get(0))
				.exists());
		Assert.assertEquals(groups.size(), 1);

		// add entities (JSON)
		List<String> jsonEntityIds = persistence.add(_TestClassTest.class,
				Arrays.asList(new _TestClassTest[] {
						new _TestClassTest(new _InnerTestClassTest("Foo", 1),
								"Max", 42, Enumeration.FIRST),
						new _TestClassTest(new _InnerTestClassTest("Foo", 2),
								"Max", 43, Enumeration.SECOND) }));
		// flush entities manually
		String groupId = persistence.flush(_TestClassTest.class);
		// a group with the entities has been created
		for (String entityId : jsonEntityIds) {
			Assert.assertTrue(new File(outputDir + "/test/" + groupId + "/"
					+ entityId).exists());
		}

		// add entities (java binary)
		List<Integer> javaBinaryList = Arrays
				.asList(new Integer[] { 10, 20, 30 });
		List<String> javaBinaryEntityIds = persistence.add(Integer.class,
				javaBinaryList);
		// flush entities manually
		groupId = persistence.flush(Integer.class);
		// a group with the entities has been created
		for (String entityId : javaBinaryEntityIds) {
			Assert.assertTrue(new File(outputDir + "/Integer/" + groupId + "/"
					+ entityId).exists());
		}

		// set new config => all entity managers are recreated
		persistence.setServerConfiguration(serverConfig, serverInstanceConfig,
				serverConfigPath, instanceConfigPath);

		// all entities must have been moved to the new entity managers
		List<Entity<Object>> entities = persistence
				.acquire(javaBinaryEntityIds);
		Assert.assertEquals(entities.size(), 3);
		persistence.release(entities, /* write */false);

		entities = persistence.acquire(jsonEntityIds);
		Assert.assertEquals(entities.size(), 2);
		persistence.release(entities, /* write */false);

		entities = persistence.acquire(xmlEntityIds);
		Assert.assertEquals(entities.size(), 1);
		persistence.release(entities, /* write */true);

		// a new group has been created because the config object has been
		// changed and auto flush is enabled
		groups = persistence.getGroups(LLRPServerConfigurationType.class);

		Assert.assertEquals(groups.size(), 2);

		// remove an entity
		Thread.sleep(50);
		List<Object> entityObjects = persistence.remove(xmlEntityIds);
		XMLSerializer<LLRPServerConfigurationType> serializer = new XMLSerializer<>(
				LLRPServerConfigurationType.class);
		// the added config is returned
		Assert.assertEquals(serializer
				.serialize((LLRPServerConfigurationType) entityObjects.get(0)),
				serializer.serialize(serverConfig));
		// a new empty group has been created (auto flush)
		groups = persistence.getGroups(LLRPServerConfigurationType.class);
		sortById(groups);
		Assert.assertEquals(new File(outputDir + "/config/"
				+ groups.get(2).getGroupId()).listFiles().length, 0);
		Assert.assertEquals(groups.size(), 3);

		// close the persistence
		persistence.close();

		// Cleanup
		_FileHelperTest.deleteFiles(outputDir);
		new File(outputDir).delete();
	}

	public void flushDelete() throws Exception {
		persistence.open();

		// add an entity (XML, auto flush)
		List<String> xmlEntityIds = persistence
				.add(LLRPServerConfigurationType.class,
						Arrays.asList(new LLRPServerConfigurationType[] { serverConfig }));
		// a group with the entity has been created
		List<EntityGroup> groups = persistence
				.getGroups(LLRPServerConfigurationType.class);
		Assert.assertTrue(new File(outputDir + "/config/"
				+ groups.get(0).getGroupId() + "/" + xmlEntityIds.get(0))
				.exists());

		// flush entities manually (time based groupId)
		Thread.sleep(50);
		String groupId = persistence.flush(LLRPServerConfigurationType.class);
		// no further group is added because the manual flush is disabled
		Assert.assertNull(groupId);
		groups = persistence.getGroups(LLRPServerConfigurationType.class);
		Assert.assertEquals(groups.size(), 1);

		// add entities (JSON)
		List<String> entityIds = persistence.add(_TestClassTest.class, Arrays
				.asList(new _TestClassTest[] { new _TestClassTest(
						new _InnerTestClassTest("Foo", 1), "Max", 42,
						Enumeration.FIRST) }));
		// flush entities manually
		String groupId1 = persistence.flush(_TestClassTest.class);
		// a group with the entity has been created
		Assert.assertTrue(new File(outputDir + "/test/" + groupId1 + "/"
				+ entityIds.get(0)).exists());
		Assert.assertEquals(groupId1.substring(4, 5), "_");

		// change the test object
		List<Entity<Object>> entities = persistence.acquire(entityIds);
		_TestClassTest test = (_TestClassTest) entities.get(0).getObject();
		test.setAbc("A");
		persistence.release(entities, /* write */true);
		// check writing
		entities = persistence.acquire(entityIds);
		test = (_TestClassTest) entities.get(0).getObject();
		Assert.assertEquals(test.getAbc(), "A");
		persistence.release(entities, /* write */false);
		// flush the entity again to a new group (time based groupId)
		Thread.sleep(50);
		String groupId2 = persistence.flush(_TestClassTest.class);
		// the group with the entity has been created
		Assert.assertTrue(new File(outputDir + "/test/" + groupId2 + "/"
				+ entityIds.get(0)).exists());
		Assert.assertNotEquals(groupId1, groupId2);

		// delete the second group
		persistence.delete(_TestClassTest.class, groupId2);
		// the group has been deleted
		Assert.assertFalse(new File(outputDir + "/test/" + groupId2).exists());

		// refresh the entity to the first version
		entityIds = persistence.refresh(_TestClassTest.class, groupId1);
		entities = persistence.acquire(entityIds);
		test = (_TestClassTest) entities.get(0).getObject();
		Assert.assertEquals(test.getAbc(), "Max");
		persistence.release(entities, /* write */false);

		// close the persistence
		persistence.close();

		// Cleanup
		_FileHelperTest.deleteFiles(outputDir);
		new File(outputDir).delete();
	}

	public void cleanUp() throws Exception {
		persistence.open();

		// add an entity (XML, auto flush, auto clean up)
		List<String> xmlEntityIds1 = persistence
				.add(LLRPServerConfigurationType.class,
						Arrays.asList(new LLRPServerConfigurationType[] { serverConfig }));
		// a group with the entity has been created
		List<EntityGroup> groups1 = persistence
				.getGroups(LLRPServerConfigurationType.class);
		String groupId1 = groups1.get(0).getGroupId();
		String pathEntity1 = outputDir + "/config/" + groupId1 + "/"
				+ xmlEntityIds1.get(0);
		Assert.assertTrue(new File(pathEntity1).exists());

		// wait for maxCreationDateInterval (3 sec)
		Thread.sleep(3100);

		// add a further entity (XML, auto flush)
		List<String> xmlEntityIds2 = persistence
				.add(LLRPServerConfigurationType.class,
						Arrays.asList(new LLRPServerConfigurationType[] { serverConfig }));
		// a group with the entity has been created
		List<EntityGroup> groups2 = persistence
				.getGroups(LLRPServerConfigurationType.class);
		String groupId2 = groups2.get(0).getGroupId();
		String pathEntity2 = outputDir + "/config/" + groupId2 + "/"
				+ xmlEntityIds2.get(0);
		Assert.assertTrue(new File(pathEntity2).exists());

		// the first group has been deleted due to auto clean up
		Assert.assertNotEquals(groupId1, groupId2);
		Assert.assertFalse(new File(pathEntity1).exists());

		// clean up manually
		persistence.cleanUp(LLRPServerConfigurationType.class);
		// the file has NOT been deleted because the manual clean up is
		// disabled
		Assert.assertTrue(new File(pathEntity2).exists());

		// add an entity (java binary, auto flush)
		List<Integer> javaBinaryList = Arrays.asList(new Integer[] { 10 });
		List<String> javaBinaryEntityIds = persistence.add(Integer.class,
				javaBinaryList);
		groupId1 = persistence.flush(Integer.class);
		// a group with the entities has been created
		pathEntity1 = outputDir + "/Integer/" + groupId1 + "/"
				+ javaBinaryEntityIds.get(0);
		Assert.assertTrue(new File(pathEntity1).exists());

		// clean up manually
		persistence.cleanUp(Integer.class);
		// the group has been deleted
		Assert.assertFalse(new File(pathEntity1).exists());

		persistence.close();

		// Cleanup
		_FileHelperTest.deleteFiles(outputDir);
		new File(outputDir).delete();
	}

	private void sortById(List<EntityGroup> groups) {
		Collections.sort(groups, new Comparator<EntityGroup>() {

			@Override
			public int compare(EntityGroup o1, EntityGroup o2) {
				return o1.getGroupId().compareTo(o2.getGroupId());
			}
		});
	}
}
