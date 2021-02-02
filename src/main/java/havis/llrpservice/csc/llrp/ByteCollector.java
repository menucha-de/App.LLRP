package havis.llrpservice.csc.llrp;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects bytes in collections. A collection must be added with
 * {@link #addCollection(Object, ByteBuffer)}. If a collection is filled up
 * using {@link #append(Object, ByteBuffer)} then it is removed from the byte
 * collector and returned.
 */
class ByteCollector {
	private Map<Object, ByteBuffer> collections = new HashMap<Object, ByteBuffer>();

	/**
	 * Adds a collection which shall be filled up.
	 * 
	 * @param key
	 * @param data
	 */
	void addCollection(Object key, ByteBuffer data) {
		collections.put(key, data);
	}

	/**
	 * Removes a collection.
	 * 
	 * @param key
	 * @return
	 */
	ByteBuffer removeCollection(Object key) {
		return collections.remove(key);
	}

	/**
	 * Returns <code>true</code> if a collection for a key exists.
	 * 
	 * @param key
	 * @return
	 */
	boolean containsKey(Object key) {
		return collections.containsKey(key);
	}

	/**
	 * Returns the count of managed collections.
	 * 
	 * @return
	 */
	int size() {
		return collections.size();
	}

	/**
	 * Appends bytes to a collection. The collection must be added with
	 * {@link #addCollection(Object, ByteBuffer)} before. If the collection is
	 * filled up then it is removed from the collector and returned. The
	 * returned collection is ready to read.
	 * 
	 * @param key
	 * @param newData
	 *            Data for the collection. The buffer must be ready to read. If
	 *            not all data are required then the remaining data are left in
	 *            the buffer (ready to read).
	 * @return The collected data or <code>null</code> if the collection has not
	 *         been filled up yet.
	 */
	ByteBuffer append(Object key, ByteBuffer newData) {
		int newByteCount = newData.remaining();
		ByteBuffer existingDataBuf = collections.get(key);
		int missingByteCount = existingDataBuf.remaining();
		// if not enough data have been received up to now
		if (missingByteCount > 0) {
			// if enough space for all new data exists
			if (newByteCount <= missingByteCount) {
				// append all new data
				existingDataBuf.put(newData);
				missingByteCount -= newByteCount;
			} else {
				// fill up the buffer with missing data
				byte[] transfer = new byte[missingByteCount];
				newData.get(transfer);
				existingDataBuf.put(transfer);
				missingByteCount = 0;
			}
		}
		if (missingByteCount <= 0) {
			collections.remove(key);
			return existingDataBuf;
		}
		return null;
	}
}
