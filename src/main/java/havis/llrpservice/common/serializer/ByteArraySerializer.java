package havis.llrpservice.common.serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Provides the serialization/deserialization of objects in byte format. Please
 * make sure to have the right object type version.
 */
public class ByteArraySerializer implements Serializable {
	private static final long serialVersionUID = -6643155452416709683L;

	/**
	 * Serialize an object to a byte array
	 * @param obj
	 * @return The serialized byte array
	 * @throws IOException
	 */
	public byte[] serialize(Object obj) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream out = new ObjectOutputStream(bos);
		out.writeObject(obj);
		out.flush();
		out.close();
		return bos.toByteArray();
	}

	/**
	 * De-serialize a byte array to an object
	 * @param bytes
	 * @return The de-serialized object
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public <T> T deserialize(byte[] bytes) throws ClassNotFoundException,
			IOException {
		ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(
				bytes));
		return (T) in.readObject();
	}

}
