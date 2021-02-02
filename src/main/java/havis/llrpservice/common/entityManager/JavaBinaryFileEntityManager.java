package havis.llrpservice.common.entityManager;

import havis.llrpservice.common.serializer.ByteArraySerializer;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class JavaBinaryFileEntityManager<T> extends FileEntityManager<T> {

	private static final Logger log = Logger.getLogger(JavaBinaryFileEntityManager.class.getName());

	private ByteArraySerializer serializer;

	public JavaBinaryFileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> fileProperties)
			throws MissingPropertyException {
		super(clazz, classVersion, fileProperties);
		init(fileProperties);
	}

	public JavaBinaryFileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> fileProperties,
			Map<String, T> initialEntities) throws MissingPropertyException {
		super(clazz, classVersion, fileProperties, initialEntities);
		init(fileProperties);
	}

	private void init(Map<FileProperty, Object> fileProperties) {
		log.log(Level.INFO, "Creating manager with file properties {0}", fileProperties);
		serializer = new ByteArraySerializer();
	}

	@Override
	byte[] serialize(T obj) throws IOException {
		return serializer.serialize(obj);
	}

	@Override
	T deserialize(byte[] obj) throws JsonParseException, JsonMappingException,
			IOException, ClassNotFoundException {
		return serializer.deserialize(obj);
	}

	@Override
	public String getContentFormat() {
		return "JavaBinary";
	}
}
