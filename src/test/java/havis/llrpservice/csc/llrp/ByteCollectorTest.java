package havis.llrpservice.csc.llrp;

import java.nio.ByteBuffer;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class ByteCollectorTest {

	/**
	 * Add a collection to a byte collector.
	 * <p>
	 * Check if the collection has been added with
	 * {@link ByteCollector#containsKey(Object)}.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The collection has been added.
	 * </ul>
	 * </p>
	 */
	public void addCollection() {
		ByteCollector bc = new ByteCollector();

		// Add a collection to a byte collector.
		ByteBuffer collection = ByteBuffer.allocate(4);
		Object key = new Object();
		bc.addCollection(key, collection);

		// Check if the collection has been added
		Assert.assertTrue(bc.containsKey(key));
		Assert.assertEquals(bc.size(), 1);
	}

	/**
	 * Add a collection to a byte collector and append some data.
	 * <p>
	 * Remove the collection. Check it with
	 * {@link ByteCollector#containsKey(Object)}.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The collection has been removed from the collection.
	 * <li>The appended data are returned.
	 * </ul>
	 * </p>
	 * Remove a collection which has not been added before.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The collection has been removed from the collection without any
	 * exceptions.
	 * </ul>
	 * </p>
	 */
	public void removeCollection() {
		ByteCollector bc = new ByteCollector();

		// Add a collection to a byte collector and append some data.
		ByteBuffer collection = ByteBuffer.allocate(10);
		Object key = new Object();
		bc.addCollection(key, collection);
		ByteBuffer data = ByteBuffer.allocate(4);
		data.putInt(0x01020304);
		data.flip();
		bc.append(key, data);
		// Remove the collection.
		ByteBuffer result = bc.removeCollection(key);
		// The collection has been removed from the collection.
		Assert.assertFalse(bc.containsKey(key));
		// The appended data are returned.
		result.flip();
		Assert.assertEquals(result.remaining(), 4);
		Assert.assertEquals(result.getInt(), 0x01020304);
		// Remove a collection which has not been added before.
		// The collection has been removed from the collection without any
		// exceptions.
		bc.removeCollection(new Object());
		Assert.assertEquals(bc.size(), 0);
	}

	/**
	 * Add a collection to a byte collector and fill it with one call of
	 * {@link ByteCollector#append(Object, ByteBuffer)} .
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The collection with the appended data is returned.
	 * <li>The collection has been removed from the collection.
	 * </ul>
	 * </p>
	 * <p>
	 * Add a new collection and fill it with multiple calls.
	 * </p>
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The collection with the appended data is returned with the last call.
	 * <li>The collection has been removed after the last call.
	 * </ul>
	 * </p>
	 */
	public void append1() {
		ByteCollector bc = new ByteCollector();

		// Add a collection to a byte collector.
		ByteBuffer collection = ByteBuffer.allocate(4);
		Object key = new Object();
		bc.addCollection(key, collection);

		// Fill it with one call
		ByteBuffer data = ByteBuffer.allocate(4);
		data.putInt(0x01020304);
		data.flip();

		// The collection with the appended data is returned.
		ByteBuffer result = bc.append(key, data);
		result.flip();
		Assert.assertEquals(result.remaining(), 4);
		Assert.assertEquals(result.getInt(), 0x01020304);
		Assert.assertEquals(data.remaining(), 0);

		// The collection has been removed from the collection.
		Assert.assertEquals(bc.size(), 0);

		// Add a new collection and fill it with multiple calls.
		collection = ByteBuffer.allocate(4);
		bc.addCollection(key, collection);

		data = ByteBuffer.allocate(2);
		data.putShort((short) 0x0102);
		data.flip();
		result = bc.append(key, data);
		Assert.assertNull(result);
		Assert.assertEquals(data.remaining(), 0);

		data = ByteBuffer.allocate(2);
		data.putShort((short) 0x0304);
		data.flip();
		result = bc.append(key, data);
		result.flip();

		// The collection with the appended data is returned with the last call.
		Assert.assertEquals(result.remaining(), 4);
		Assert.assertEquals(result.getInt(), 0x01020304);
		Assert.assertEquals(data.remaining(), 0);

		// The collection has been removed after the last call.
		Assert.assertEquals(bc.size(), 0);
	}

	/**
	 * Add a collection and fill it with multiple calls. The last call contains
	 * more data than required.
	 * <p>
	 * Expected:
	 * <ul>
	 * <li>The collection is returned with the last call. It contains the
	 * required data. The remaining data are left in the original byte buffer.
	 * <li>The collection has been removed after the last call.
	 * </ul>
	 * </p>
	 */
	public void append2() {
		ByteCollector bc = new ByteCollector();

		// Add a new collection and fill it with multiple calls.
		ByteBuffer collection = ByteBuffer.allocate(4);
		Object key = new Object();
		bc.addCollection(key, collection);

		ByteBuffer data = ByteBuffer.allocate(2);
		data.putShort((short) 0x0102);
		data.flip();
		ByteBuffer result = bc.append(key, data);
		Assert.assertNull(result);

		data = ByteBuffer.allocate(4);
		data.putInt(0x03040506);
		data.flip();
		result = bc.append(key, data);
		result.flip();

		// The collection is returned with the last call. It contains the
		// required data. The remaining data are left in the original byte
		// buffer.
		Assert.assertEquals(result.remaining(), 4);
		Assert.assertEquals(result.getInt(), 0x01020304);
		Assert.assertEquals(data.remaining(), 2);
		Assert.assertEquals(data.getShort(), (short) 0x0506);

		// The collection has been removed after the last call.
		Assert.assertEquals(bc.size(), 0);
	}
}
