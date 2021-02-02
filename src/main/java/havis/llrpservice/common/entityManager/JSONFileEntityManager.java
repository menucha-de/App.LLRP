package havis.llrpservice.common.entityManager;

import havis.llrpservice.common.serializer.JsonSerializer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class JSONFileEntityManager<T> extends FileEntityManager<T> {

	private static final Logger log = Logger.getLogger(JSONFileEntityManager.class.getName());

	public enum JsonProperty {
		/**
		 * The encoding of the entity files
		 */
		ENCODING,
		/**
		 * A configured {@link JsonSerializer} (optional)
		 */
		SERIALIZER
	}

	private Charset encoding;
	private JsonSerializer serializer;

	/**
	 * Initialize JSONFileEntityManager
	 * 
	 * @param clazz
	 * @param classVersion
	 * @param fileProperties
	 * @param jsonProperties
	 *            Sets up the encoding and the serializer
	 * @throws MissingPropertyException
	 */
	public JSONFileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> fileProperties,
			Map<JsonProperty, Object> jsonProperties)
			throws MissingPropertyException {
		super(clazz, classVersion, fileProperties);
		init(clazz, fileProperties, jsonProperties);
	}

	public JSONFileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> fileProperties,
			Map<JsonProperty, Object> jsonProperties,
			Map<String, T> initialEntities) throws MissingPropertyException {
		super(clazz, classVersion, fileProperties, initialEntities);
		init(clazz, fileProperties, jsonProperties);
	}

	private void init(Class<T> clazz, Map<FileProperty, Object> fileProperties,
			Map<JsonProperty, Object> jsonProperties)
			throws MissingPropertyException {
		log.log(Level.INFO, "Creating manager with file properties {0} and JSON properties {1}", new Object[]{ fileProperties, jsonProperties });

		if (jsonProperties.containsKey(JsonProperty.ENCODING)) {
			encoding = (Charset) jsonProperties.get(JsonProperty.ENCODING);
		} else {
			throw new MissingPropertyException("Missing property: "
					+ JsonProperty.ENCODING.name());
		}

		if (jsonProperties.containsKey(JsonProperty.SERIALIZER)) {
			serializer = (JsonSerializer) jsonProperties
					.get(JsonProperty.SERIALIZER);
		} else {
			serializer = new JsonSerializer(clazz);
		}
	}

	@Override
	byte[] serialize(T obj) throws IOException {
		byte[] result = serializer.serialize(obj).getBytes(encoding);
		if (log.isLoggable(Level.FINE))
			log.log(Level.FINE, "Serialized data: {0}", new String(result, encoding));
		return result;
	}

	@Override
	T deserialize(byte[] obj) throws JsonParseException, JsonMappingException,
			IOException {
		if (log.isLoggable(Level.FINE))
			log.log(Level.FINE, "Deserializing data: {0}", new String(obj, encoding));
		return serializer.deserialize(new String(obj, encoding));
	}

	@Override
	public String getContentFormat() {
		return "JSON";
	}

}
