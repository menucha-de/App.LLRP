package havis.llrpservice.common.entityManager;

import havis.llrpservice.common.serializer.XMLSerializer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.jibx.runtime.JiBXException;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class XMLFileEntityManager<T> extends FileEntityManager<T> {

	private static final Logger log = Logger.getLogger(XMLFileEntityManager.class.getName());

	public enum XmlProperty {
		/**
		 * The encoding of the entity files
		 */
		ENCODING
	}

	private Charset encoding;
	private XMLSerializer<T> serializer;

	/**
	 * Initialize JSONFileEntityManager
	 * 
	 * @param clazz
	 * @param classVersion
	 * @param fileProperties
	 * @param xmlProperties
	 *            Sets up the encoding and the serializer
	 * @throws MissingPropertyException
	 * @throws JiBXException
	 */
	public XMLFileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> fileProperties,
			Map<XmlProperty, Object> xmlProperties)
			throws MissingPropertyException, JiBXException {
		super(clazz, classVersion, fileProperties);
		init(clazz, fileProperties, xmlProperties);

	}

	public XMLFileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> fileProperties,
			Map<XmlProperty, Object> xmlProperties,
			Map<String, T> initialEntities) throws MissingPropertyException,
			JiBXException {
		super(clazz, classVersion, fileProperties, initialEntities);
		init(clazz, fileProperties, xmlProperties);

	}

	private void init(Class<T> clazz, Map<FileProperty, Object> fileProperties,
			Map<XmlProperty, Object> xmlProperties)
			throws MissingPropertyException, JiBXException {
		if (log.isLoggable(Level.INFO))
			log.log(Level.INFO, "Creating manager with file properties {0} and XML properties {1}", new Object[]{ fileProperties, xmlProperties });

		if (xmlProperties.containsKey(XmlProperty.ENCODING)) {
			encoding = (Charset) xmlProperties.get(XmlProperty.ENCODING);
		} else {
			throw new MissingPropertyException("Missing property: "
					+ XmlProperty.ENCODING.name());
		}

		serializer = new XMLSerializer<>(clazz);
	}

	@Override
	byte[] serialize(T obj) throws IOException, JiBXException,
			ParserConfigurationException, SAXException {
		byte[] result = serializer.serialize(obj).getBytes(encoding);
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "Serialized data: {0}", new String(result, encoding));
		}
		return result;
	}

	@Override
	T deserialize(byte[] obj) throws JsonParseException, JsonMappingException,
			IOException, JiBXException, SAXException {
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "Deserializing data: {0}", new String(obj, encoding));
		}
		return serializer.deserialize(new String(obj, encoding));
	}

	@Override
	public String getContentFormat() {
		return "XML";
	}

}
