package havis.llrpservice.common.entityManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import havis.llrpservice.common.entityManager.FileEntityManager.FileProperty;
import havis.llrpservice.common.entityManager._TestClassTest.Enumeration;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Invocation;

public class FileEntityManagerTest {
	// Entity Manager
	private FileEntityManager<_TestClassTest> manager;

	// Output Directory
	private final String outputDir = "output";
	// Entity object class Version
	private final String version = "1.0";

	// Sample Implementation from abstract class FileEntityManager
	private class FileEntityManagerTestClass extends FileEntityManager<_TestClassTest> {

		// Constructor only calls super
		public FileEntityManagerTestClass(String version, Map<FileProperty, Object> properties)
				throws Exception {
			super(_TestClassTest.class, version, properties);
		}

		// Constructor only calls super
		public FileEntityManagerTestClass(String version, Map<FileProperty, Object> properties,
				Map<String, _TestClassTest> initialEntries) throws Exception {
			super(_TestClassTest.class, version, properties, initialEntries);
		}

		// Override abstract method only returns given byte array instead of
		// serialized String
		@Override
		byte[] serialize(_TestClassTest obj) throws Exception {
			return "HugoText".getBytes();
		}

		// Override abstract method only returns given Object instead of
		// deserialized object
		@Override
		_TestClassTest deserialize(byte[] obj) throws Exception {
			return new _TestClassTest(new _InnerTestClassTest("Hugo", 23), "Max", 25,
					Enumeration.FIRST);
		};

		@Override
		public String getContentFormat() {
			return "Test";
		}
	}

	@BeforeClass
	public void init() throws Exception {
		// Initialize Manager properties
		Map<FileProperty, Object> properties = new HashMap<>();
		properties.put(FileProperty.BASEDIR, outputDir);

		// Instantiate Manager
		manager = new FileEntityManagerTestClass(version, properties);
		cleanUp();

		// load system properties before the class "Properties" or "File" is
		// mocked
		// Paths.get("./huhu");
	}

	@AfterClass
	public void cleanUp() throws IOException {
		// Remove output directory
		try {
			_FileHelperTest.deleteFiles(outputDir);
			new File(outputDir).delete();
		} catch (Exception e) {

		}
	}

	@Test
	public void test() {
		Pattern pattern = Pattern.compile("(\\d+)(\\.\\d+(\\.\\d+)?)?");
		Matcher m = pattern.matcher("1.2.3");
		Assert.assertTrue(m.matches());
		Assert.assertEquals(m.group(1), "1");
	}

	@Test
	@SuppressWarnings("serial")
	public void constructor() throws Exception {
		// create an entity manager with an initial entity
		final String entityId = "a";
		FileEntityManagerTestClass localManager = new FileEntityManagerTestClass(version,
				new HashMap<FileProperty, Object>() {
					{
						put(FileProperty.BASEDIR, outputDir + "/HelloWorld/BaseDir/Hugo");
					}
				}, new HashMap<String, _TestClassTest>() {
					{
						put(entityId, new _TestClassTest(new _InnerTestClassTest("Foo", 65535),
								"ABC1", 32767, Enumeration.FIRST));
					}
				});

		// the entity can be acquired
		localManager.open();
		List<Entity<_TestClassTest>> entities = localManager
				.acquire(Arrays.asList(new String[] { entityId }));
		Assert.assertEquals(entities.size(), 1);
		Assert.assertEquals(entities.get(0).getEntityId(), entityId);
		localManager.release(entities, /* write */false);
		localManager.close();

		// Cleanup
		cleanUp();
	}

	@Test
	public void constructorError() throws Exception {
		// Empty properties
		try {
			new FileEntityManagerTestClass(version, new HashMap<FileProperty, Object>());
			Assert.fail();
		} catch (MissingPropertyException e) {
			if (!e.getMessage().contains("BASEDIR")) {
				Assert.fail();
			}
		} catch (Exception e) {
			Assert.fail();
		}
	}

	@Test
	public void open() throws Exception {
		// Open basedir
		manager.open();
		File file = new File(outputDir + "/.meta");
		Assert.assertTrue(file.exists());
		file.delete();

		// Create new nested basedir
		String basedir = outputDir + "/HelloWorld/BaseDir/Hugo";
		Map<FileProperty, Object> properties = new HashMap<>();
		properties.put(FileProperty.BASEDIR, basedir);
		FileEntityManagerTestClass localManager = new FileEntityManagerTestClass(version,
				properties);

		localManager.open();
		Assert.assertTrue(new File(basedir).exists());

		// read meta file and check content
		String result = _FileHelperTest.readFile(basedir + "/.meta");
		Assert.assertTrue(result.contains("classVersion=1.0")
				&& result.contains("className=" + _TestClassTest.class.getName())
				&& result.contains("contentFormat=Test"));

		localManager.close();
		manager.close();

		// Check meta informations
		String changed = result.replace("classVersion=1.0", "classVersion=1.1");
		_FileHelperTest.writeFile(basedir + "/.meta", changed);
		localManager.open();
		localManager.close();

		changed = result.replace("classVersion=1.0", "classVersion=2.0");
		_FileHelperTest.writeFile(basedir + "/.meta", changed);
		try {
			localManager.open();
			Assert.fail();
		} catch (WrongMetaDataException e) {
			Assert.assertTrue(e.getMessage().contains("Incompatible class version '2.0'"));
		}
		changed = result.replace("contentFormat=Test", "contentFormat=Foo");
		_FileHelperTest.writeFile(basedir + "/.meta", changed);
		try {
			localManager.open();
			Assert.fail();
		} catch (WrongMetaDataException e) {
			Assert.assertTrue(e.getMessage().contains("Incompatible content format 'Foo'"));
		}
		changed = result.replace("className=havis.llrpservice.common.entityManager._TestClassTest",
				"className=havis.llrpservice.common.entityManager._Bar");
		_FileHelperTest.writeFile(basedir + "/.meta", changed);
		try {
			localManager.open();
			Assert.fail();
		} catch (WrongMetaDataException e) {
			Assert.assertTrue(e.getMessage().contains(
					"Incompatible class name havis.llrpservice.common.entityManager._Bar"));
		}

		localManager.close();

		// Cleanup
		cleanUp();
	}

	// Test multiple access to add and remove
	@Test(invocationCount = 100, threadPoolSize = 4)
	public void addRemove() throws Exception {
		// Add an empty entity list
		List<_TestClassTest> entities = new ArrayList<>();
		List<String> entityIds = manager.add(entities);
		// No new entities added
		Assert.assertEquals(entityIds.size(), 0);

		// Add local entities
		entities.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535), "ABC1", 32767,
				Enumeration.FIRST));
		entities.add(new _TestClassTest(new _InnerTestClassTest("Bar", 42), "ABC2", 16535,
				Enumeration.SECOND));
		entityIds = manager.add(entities);
		// Check if entities were added
		Assert.assertEquals(entityIds.size(), 2);
		// Add the same entities a second time
		entityIds = manager.add(entities);
		// Check if entities were added
		Assert.assertEquals(entityIds.size(), 2);

		// Remove the added entities
		List<_TestClassTest> removed = manager.remove(entityIds);
		Assert.assertEquals(removed.size(), 2);

		// Check, if the removed entities are equal to the given entities
		Assert.assertEquals(removed.get(0), entities.get(0));
		Assert.assertEquals(removed.get(1), entities.get(1));

		// Try to remove the same entities as before (they are missing now)
		try {
			removed = manager.remove(entityIds);
			Assert.fail();
		} catch (UnknownEntityException e) {
			if (!e.getMessage().contains("Entity unknown"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}
	}

	@Test
	public void acquireRelease() throws Exception {
		// add entities and acquire them
		List<_TestClassTest> l = new ArrayList<>();
		l.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535), "ABC1", 32767,
				Enumeration.FIRST));
		l.add(new _TestClassTest(new _InnerTestClassTest("Bar", 42), "ABC2", 16535,
				Enumeration.SECOND));
		List<String> entityIds = manager.add(l);
		List<Entity<_TestClassTest>> acquiredEntities = manager.acquire(entityIds);
		// copies of the original objects are returned
		Assert.assertNotEquals(acquiredEntities.get(0), l.get(0));
		Assert.assertNotEquals(acquiredEntities.get(1), l.get(1));
		Assert.assertEquals(acquiredEntities.get(0).getObject().getAbc(), "ABC1");
		Assert.assertEquals(acquiredEntities.get(1).getObject().getAbc(), "ABC2");

		// change an entity and write the entities back
		l.get(1).setAbc("ABC1Changed");
		manager.release(acquiredEntities, /* write */true);

		// try to write the entities again
		try {
			manager.release(acquiredEntities, /* write */true);
			Assert.fail();
		} catch (StaleEntityStateException e) {
			Assert.assertTrue(e.getMessage().contains(acquiredEntities.get(0).getEntityId()));
		} catch (Exception e) {
			Assert.fail();
		}

		// release the entities without writing modifications
		manager.release(acquiredEntities, /* write */false);
	}

	@Test
	public void flushRefresh() throws Exception {
		// Open manager (create basedir)
		manager.open();

		// add entities and flush them to group "Hugo"
		List<_TestClassTest> l = new ArrayList<>();
		l.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535), "ABC1", 32767,
				Enumeration.FIRST));
		l.add(new _TestClassTest(new _InnerTestClassTest("Bar", 42), "ABC2", 16535,
				Enumeration.SECOND));
		List<String> entityIds = manager.add(l);
		manager.flush("Hugo", entityIds);
		for (String entityId : entityIds) {
			Assert.assertTrue(new File(outputDir + "/" + "Hugo/" + entityId).exists());
		}

		// flush only one of the entities to the same group
		List<String> copiedIds = new ArrayList<>(entityIds);
		String removedEntityId = copiedIds.remove(0);
		manager.flush("Hugo", copiedIds);
		// the other entities has been deleted
		Assert.assertFalse(new File(outputDir + "/" + "Hugo/" + removedEntityId).exists());

		// Flush all entities again
		manager.flush("Hugo", entityIds);
		// the removed entity has been flushed again
		String content = _FileHelperTest.readFile(outputDir + "/" + "Hugo/" + entityIds.get(0));
		Assert.assertEquals(content, "HugoText");

		// Create wrong entities
		List<String> errorEntities = new ArrayList<>();
		errorEntities.add("Oh");
		errorEntities.add("ha");

		// Try to flush these entities
		try {
			manager.flush("Hugo", errorEntities);
			Assert.fail();
		} catch (UnknownEntityException e) {
			if (!e.getMessage().contains("Oh"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}

		// remove an entity and refresh all entities from storage
		manager.remove(copiedIds);
		manager.refresh("Hugo");
		// the removed entity has been loaded
		List<Entity<_TestClassTest>> acquiredEntities = manager.acquire(entityIds);
		Assert.assertEquals(acquiredEntities.size(), 2);
		Assert.assertEquals(acquiredEntities.get(0).getEntityId(), entityIds.get(0));
		Assert.assertEquals(acquiredEntities.get(1).getEntityId(), entityIds.get(1));
		manager.release(acquiredEntities, /* write */false);

		// Try to refresh entities with unknown group
		try {
			manager.refresh("Max");
			Assert.fail();
		} catch (UnknownGroupException e) {
			if (!e.getMessage().contains("Max"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}

		// Clean up
		manager.remove(entityIds);
		manager.close();

		cleanUp();
	}

	@Test
	public void flushRefreshErrors() throws Exception {
		class Data {
			IOException exception = new IOException("huhu");
		}
		final Data data = new Data();

		new Expectations(Files.class) {
			{
				Files.delete(withInstanceOf(Path.class));
				result = new Delegate<Files>() {
					@SuppressWarnings("unused")
					void delete(Invocation inv, Path p) throws IOException {
						if (data.exception != null) {
							throw data.exception;
						}
						inv.proceed();
					}
				};
			}
		};

		// Open manager (create basedir)
		manager.open();

		// add entities and flush them to group "Hugo"
		List<_TestClassTest> l = new ArrayList<>();
		l.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535), "ABC1", 32767,
				Enumeration.FIRST));
		l.add(new _TestClassTest(new _InnerTestClassTest("Bar", 42), "ABC2", 16535,
				Enumeration.SECOND));
		List<String> entityIds = manager.add(l);
		manager.flush("Hugo", entityIds);

		// try to flush only one of the entities to the same group
		try {
			List<String> copiedIds = new ArrayList<>(entityIds);
			copiedIds.remove(0);
			manager.flush("Hugo", copiedIds);
			Assert.fail();
		} catch (EntityManagerException e) {
			Assert.assertEquals(e.getCause().getClass(), IOException.class);
		} catch (Exception e) {
			Assert.fail();
		}

		manager.close();

		// Cleanup
		data.exception = null;
		cleanUp();
	}

	// Test getGroups and delete
	@Test
	public void getGroupsDelete() throws Exception {
		// Try to get groups, but output dir does not exists because the manager
		// is closed
		try {
			manager.getGroups();
			Assert.fail();
		} catch (EntityManagerException e) {
			Assert.assertEquals(e.getCause().getClass(), NoSuchFileException.class);
		} catch (Exception e) {
			Assert.fail();
		}

		// create output dir
		manager.open();
		// No groups in output dir
		List<EntityGroup> groups = manager.getGroups();
		Assert.assertEquals(groups.size(), 0);

		// Create groups (dirs) after one second
		Date now = new Date();
		Thread.sleep(1000);
		new File(outputDir, "Group1").mkdir();
		new File(outputDir, "Group2").mkdir();
		new File(outputDir, "Group3").mkdir();

		// Get groups
		groups = manager.getGroups();
		Assert.assertEquals(groups.size(), 3);

		// Check, if all groups exists and are younger than compared date
		for (EntityGroup group : groups) {
			if (!group.getGroupId().equals("Group1") && !group.getGroupId().equals("Group2")
					&& !group.getGroupId().equals("Group3"))
				Assert.fail();
			else {
				Assert.assertTrue(now.compareTo(group.getCreationDate()) < 0);
			}
		}

		// Try to delete an unexisting group
		try {
			manager.delete("Oh");
			Assert.fail();
		} catch (UnknownGroupException e) {
			if (!e.getMessage().contains("Unknown group: Oh")) {
				Assert.fail();
			}
		} catch (Exception e) {
			Assert.fail();
		}

		// Delete Group 1
		manager.delete("Group1");
		groups = manager.getGroups();
		Assert.assertEquals(groups.size(), 2);
		// The other groups must exists
		for (EntityGroup group : groups) {
			if (!group.getGroupId().equals("Group2") && !group.getGroupId().equals("Group3")) {
				Assert.fail();
			}
		}
		// Delete Group 2
		manager.delete("Group2");
		groups = manager.getGroups();
		Assert.assertEquals(groups.size(), 1);
		// Last group must be Group 3
		if (!groups.get(0).getGroupId().equals("Group3")) {
			Assert.fail();
		}
		// Delete last group
		manager.delete("Group3");
		groups = manager.getGroups();
		Assert.assertEquals(groups.size(), 0);

		// Cleanup
		cleanUp();
	}

	@Test
	public void deleteErrors() throws Exception {

		new Expectations(Files.class) {
			{
				Files.delete(withInstanceOf(Path.class));
				result = new IOException("huhu");
			}
		};

		manager.open();

		// add entities and flush them to group "Hugo"
		List<_TestClassTest> l = new ArrayList<>();
		l.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535), "ABC1", 32767,
				Enumeration.FIRST));
		List<String> entityIds = manager.add(l);
		manager.flush("Hugo", entityIds);

		// try to delete the group via the manager
		try {
			manager.delete("Hugo");
			Assert.fail();
		} catch (EntityManagerException e) {
			Assert.assertEquals(e.getCause().getClass(), IOException.class);
		} catch (Exception e) {
			Assert.fail();
		}

		manager.close();

		// clean up
		new Expectations() {
			{
				Files.delete(withInstanceOf(Path.class));
				result = new Delegate<Files>() {
					@SuppressWarnings("unused")
					void delete(Invocation inv, Path p) throws IOException {
						inv.proceed();
					}
				};
			}
		};

		cleanUp();
	}
}
