package havis.llrpservice.common.serializer;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Provides the serialization/deserialization of objects in JSON format. This
 * implementation uses the jackson JSON processor. Changes of optional parameter
 * (e.g. pretty print, mix-ins) is not possible after the first
 * serialization/deserialize. In this case, re-initialize the serializer first.
 */
public class JsonSerializer implements Serializable {
	private static final long serialVersionUID = -6643155452416709683L;

	// Create ObjectMapper, one for serialization and one for deserialization
	ObjectMapper serMapper;
	ObjectMapper desMapper;

	// Class Type
	private final Class<?> clazz;

	/**
	 * Initializes the serializer with a special class type. This class type
	 * will be used to deserialize JSON-strings
	 * 
	 * @param clazz
	 */
	public <T> JsonSerializer(Class<T> clazz) {
		desMapper = new ObjectMapper();
		serMapper = new ObjectMapper();
		serMapper.enableDefaultTyping();
		desMapper.enableDefaultTyping();
		this.clazz = clazz;
	}

	/**
	 * Enables/Disables the prettyPrint for the JSON structure
	 * 
	 * @param enable
	 */
	public void setPrettyPrint(boolean enable) {
		if (enable)
			serMapper.enable(SerializationFeature.INDENT_OUTPUT);
	}

	/**
	 * Adds mix ins to serializer
	 * 
	 * @param mixIns
	 */
	public void addSerializerMixIns(Map<Class<?>, Class<?>> mixIns) {
		if (mixIns != null) {
			serMapper.setMixInAnnotations(mixIns);
		}
	}

	/**
	 * Adds mix ins to deserializer
	 * 
	 * @param mixIns
	 */
	public void addDeserializerMixIns(Map<Class<?>, Class<?>> mixIns) {
		if (mixIns != null) {
			desMapper.setMixInAnnotations(mixIns);
		}
	}

	/**
	 * Change mapper features for serializer
	 * 
	 * @param mapFeature
	 * @param flag
	 */
	public void setSerMapperFeature(MapperFeature mapFeature, boolean flag) {
		serMapper.configure(mapFeature, flag);
	}

	/**
	 * Change mapper features for deserializer
	 * 
	 * @param mapFeature
	 * @param flag
	 */
	public void setDesMapperFeature(MapperFeature mapFeature, boolean flag) {
		desMapper.configure(mapFeature, flag);
	}

	/**
	 * Serialize an object to a JSON-string
	 * 
	 * @param obj
	 * @return The serialized string
	 * @throws IOException
	 */
	public String serialize(Object obj) throws IOException {
		return serMapper.writeValueAsString(obj);
	}

	/**
	 * Deserialize a JSON-String to an object
	 * 
	 * @param json
	 * @return The de-serialized object
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public <T> T deserialize(String json) throws JsonParseException,
			JsonMappingException, IOException {
		return (T) desMapper.readValue(json, clazz);
	}

}
