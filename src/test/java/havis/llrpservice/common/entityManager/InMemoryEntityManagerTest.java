package havis.llrpservice.common.entityManager;

import havis.llrpservice.common.entityManager._TestClassTest.Enumeration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test
public class InMemoryEntityManagerTest {
	// Entity Manager
	private InMemoryEntityManager<_TestClassTest> manager;

	// Sample Implementation from abstract class InMemoryEntityManager
	private class InMemoryManagerTestClass extends
			InMemoryEntityManager<_TestClassTest> {

		// Constructor only calls super
		public InMemoryManagerTestClass() throws Exception {
			super();
		}

		// Constructor only calls super
		public InMemoryManagerTestClass(
				Map<String, _TestClassTest> initialEntries) throws Exception {
			super(initialEntries);
		}
	}

	@BeforeClass
	public void init() throws Exception {
		// Instantiate Manager
		manager = new InMemoryManagerTestClass();
		// load system properties before the class "Properties" or "File" is
		// mocked
		// Paths.get("./huhu");
	}

	@SuppressWarnings("serial")
	public void constructor() throws Exception {
		// create an entity manager with an initial entity
		final String entityId = "a";
		InMemoryManagerTestClass localManager = new InMemoryManagerTestClass(
				new HashMap<String, _TestClassTest>() {
					{
						put(entityId, new _TestClassTest(
								new _InnerTestClassTest("Foo", 65535), "ABC1",
								32767, Enumeration.FIRST));
					}
				});

		// the entity can be acquired
		localManager.open();
		List<Entity<_TestClassTest>> entities = localManager.acquire(Arrays
				.asList(new String[] { entityId }));
		Assert.assertEquals(entities.size(), 1);
		Assert.assertEquals(entities.get(0).getEntityId(), entityId);
		localManager.release(entities, /* write */false);
		localManager.close();
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
		entities.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535),
				"ABC1", 32767, Enumeration.FIRST));
		entities.add(new _TestClassTest(new _InnerTestClassTest("Bar", 42),
				"ABC2", 16535, Enumeration.SECOND));
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

	public void acquireRelease() throws Exception {
		// add entities and acquire them
		List<_TestClassTest> l = new ArrayList<>();
		l.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535), "ABC1",
				32767, Enumeration.FIRST));
		l.add(new _TestClassTest(new _InnerTestClassTest("Bar", 42), "ABC2",
				16535, Enumeration.SECOND));
		List<String> entityIds = manager.add(l);
		List<Entity<_TestClassTest>> acquiredEntities = manager
				.acquire(entityIds);
		// copies of the original objects are returned
		Assert.assertNotEquals(acquiredEntities.get(0), l.get(0));
		Assert.assertNotEquals(acquiredEntities.get(1), l.get(1));
		Assert.assertEquals(acquiredEntities.get(0).getObject().getAbc(),
				"ABC1");
		Assert.assertEquals(acquiredEntities.get(1).getObject().getAbc(),
				"ABC2");

		// change an entity and write the entities back
		l.get(1).setAbc("ABC1Changed");
		manager.release(acquiredEntities, /* write */true);

		// try to write the entities again
		try {
			manager.release(acquiredEntities, /* write */true);
			Assert.fail();
		} catch (StaleEntityStateException e) {
			Assert.assertTrue(e.getMessage().contains(
					acquiredEntities.get(0).getEntityId()));
		} catch (Exception e) {
			Assert.fail();
		}

		// release the entities without writing modifications
		manager.release(acquiredEntities, /* write */false);
	}

	public void flushRefresh() throws Exception {
		// Open manager (create basedir)
		manager.open();
		// add entities and flush them to group "Hugo"
		List<_TestClassTest> l = new ArrayList<>();
		l.add(new _TestClassTest(new _InnerTestClassTest("Foo", 65535), "ABC1",
				32767, Enumeration.FIRST));
		l.add(new _TestClassTest(new _InnerTestClassTest("Bar", 42), "ABC2",
				16535, Enumeration.SECOND));
		List<String> entityIds = manager.add(l);
		manager.flush("Hugo", entityIds);
		Assert.assertEquals(new ArrayList<>().size(), manager.refresh("Hugo")
				.size());
		manager.close();
	}

	// Test getGroups and delete (nothing will hapen)
	public void getGroupsDelete() throws Exception {
		manager.getGroups();
		Assert.assertEquals(new ArrayList<>().size(), manager.getGroups()
				.size());
		manager.delete("Hugo");
	}
}
