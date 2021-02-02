package havis.llrpservice.server.persistence;

import havis.llrpservice.common.entityManager.Entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mockit.Capturing;
import mockit.Mock;
import mockit.MockUp;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class ObservablePersistenceTest {

	private class PersistenceListenerImpl implements PersistenceListener {
		private List<String> addedEntityIds = new ArrayList<>();
		private List<String> removedEntityIds = new ArrayList<>();
		private List<String> updatedEntityIds = new ArrayList<>();

		@Override
		public void added(ObservablePersistence src, List<String> entityIds) {
			addedEntityIds.addAll(entityIds);
		}

		@Override
		public void removed(ObservablePersistence src, List<String> entityIds) {
			removedEntityIds.addAll(entityIds);
		}

		@Override
		public void updated(ObservablePersistence src, List<String> entityIds) {
			updatedEntityIds.addAll(entityIds);
		}
	}

	public void test(@Capturing final Entity<Object> entity1,
			@Capturing final Entity<Object> entity2) throws Exception {
		final List<Object> entityObjects = Arrays.asList(new Object[] {
				"Hello", "World" });
		final List<String> entityIds = Arrays
				.asList(new String[] { "e1", "e2" });
		new MockUp<Persistence>() {

			String groupId = "g1";
			@SuppressWarnings("serial")
			List<Entity<Object>> l = new ArrayList<Entity<Object>>() {
				{
					add(entity1);
					add(entity2);
				}
			};

			@Mock
			public List<String> add(Class<String> clazz, List<String> entities) {
				return entityIds;
			}

			@Mock
			public String flush(Class<String> clazz) {
				return groupId;
			}

			@Mock
			public List<String> refresh(Class<String> clazz, String groupId) {
				return entityIds;
			}

			@Mock
			public List<Entity<Object>> acquire(List<String> entityIds) {
				return l;
			}

			@Mock
			public List<Object> remove(List<String> entityIds) {
				return Arrays.asList(new Object[] { entityObjects.get(0) });
			}

			@Mock
			public void release(List<Entity<Object>> entities, boolean write) {
			}
		};
		new NonStrictExpectations() {
			{
				entity1.getEntityId();
				result = entityIds.get(0);
				entity1.getObject();
				result = entityObjects.get(0);

				entity2.getEntityId();
				result = entityIds.get(1);
				entity2.getObject();
				result = entityObjects.get(1);
			}
		};

		ObservablePersistence obs = new ObservablePersistence();
		obs.addClass(String.class, "1.0");

		// add 2 listeners
		PersistenceListenerImpl listener1 = new PersistenceListenerImpl();
		PersistenceListenerImpl listener2 = new PersistenceListenerImpl();
		obs.addListener(listener1, String.class);
		obs.addListener(listener1, String.class);
		obs.addListener(listener2, String.class);

		// add entities
		List<String> entityIds1 = obs.add(String.class,
				Arrays.asList(new String[] { "Hello", "World" }));
		List<String> entityIds2 = new ArrayList<>(entityIds1);
		entityIds2.addAll(entityIds1);
		// "added" events for all listeners must be fired
		Assert.assertEquals(listener1.addedEntityIds, entityIds2);
		Assert.assertEquals(listener2.addedEntityIds, entityIds1);

		// flush and refresh the entities
		String groupId = obs.flush(String.class);
		obs.refresh(String.class, groupId);
		// "updated" events for all listeners must be fired
		Assert.assertEquals(listener1.updatedEntityIds, entityIds2);
		Assert.assertEquals(listener2.updatedEntityIds, entityIds1);

		// acquire and release the entities without modifications
		List<Entity<Object>> entitiesList = obs.acquire(entityIds1);
		obs.release(entitiesList, /* write */false);
		// no event is fired
		Assert.assertEquals(listener1.updatedEntityIds, entityIds2);
		Assert.assertEquals(listener2.updatedEntityIds, entityIds1);

		// acquire and release the entities with modifications
		entitiesList = obs.acquire(entityIds1);
		obs.release(entitiesList, /* write */true);
		// "updated" events for all listeners must be fired
		List<String> entityIds4 = new ArrayList<>(entityIds2);
		entityIds4.addAll(entityIds2);
		Assert.assertEquals(listener1.updatedEntityIds, entityIds4);
		Assert.assertEquals(listener2.updatedEntityIds, entityIds2);

		// remove first entity
		List<String> removedEntityIds1 = Arrays
				.asList(new String[] { entityIds1.get(0) });
		// "removed" events for all listeners must be fired
		List<String> removedEntityIds2 = new ArrayList<>(removedEntityIds1);
		removedEntityIds2.addAll(removedEntityIds1);
		obs.remove(removedEntityIds1);
		Assert.assertEquals(listener1.removedEntityIds, removedEntityIds2);
		Assert.assertEquals(listener2.removedEntityIds, removedEntityIds1);

		// remove first listener and add an entity
		obs.removeListener(listener1, String.class);
		obs.add(String.class, Arrays.asList(new String[] { "oh" }));
		// only the second listener receives an event
		Assert.assertEquals(listener1.addedEntityIds, entityIds2);
		Assert.assertEquals(listener2.addedEntityIds, entityIds2);
	}
}
