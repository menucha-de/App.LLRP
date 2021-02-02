package havis.llrpservice.common.serializer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.jibx.runtime.BindingDirectory;
import org.jibx.runtime.IBindingFactory;
import org.jibx.runtime.IMarshallingContext;
import org.jibx.runtime.IUnmarshallingContext;
import org.jibx.runtime.JiBXException;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Provides the serialization/deserialization of objects in XML format.
 */
public class XMLSerializer<T> implements Serializable {
	private static final long serialVersionUID = -6643155452416709683L;

	/**
	 * pretty print feature
	 */
	private boolean prettyPrint;

	/**
	 * validation variables
	 */
	private Schema schema;

	/**
	 * charset for the serialization
	 */
	private Charset charset = StandardCharsets.UTF_8;

	/**
	 * marshalling/unmarshalling variables
	 */
	private final IBindingFactory bFact;
	private final IMarshallingContext mctx;

	/**
	 * Set the charset to be used.
	 * 
	 * @param charset
	 */
	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	/**
	 * Enables the pretty print feature of the serializer. Attention! The pretty
	 * print feature uses dom trees to provide the pretty print. An activation
	 * causes a significant loss of performance.
	 * 
	 * @param enable
	 */
	public void setPrettyPrint(boolean enable) {
		prettyPrint = enable;
	}

	/**
	 * Set a schema for xsd validation. If set to null, validation will be
	 * deactivated
	 * 
	 * @param xsdFile
	 *            XSD-File
	 * @throws SAXException
	 */
	public void setSchema(File xsdFile) throws SAXException {
		if (xsdFile != null) {
			SchemaFactory factory = SchemaFactory
					.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			schema = factory.newSchema(xsdFile);
		} else {
			schema = null;
		}
	}

	/**
	 * Initialize the serializer with special class type and create the
	 * marshalling object for that class type
	 * 
	 * @param clazz
	 * @throws JiBXException
	 */
	public XMLSerializer(Class<T> clazz) throws JiBXException {
		bFact = BindingDirectory.getFactory(clazz);
		mctx = bFact.createMarshallingContext();
	}

	/**
	 * Serialize an object to a XML-string. If pretty print is activated, the
	 * output will be normalized an pretty.
	 * 
	 * @param obj
	 * @return The serialized string
	 * @throws IOException
	 * @throws JiBXException
	 * @throws XMLNormalizationException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	public String serialize(T obj) throws IOException, JiBXException,
			ParserConfigurationException, SAXException {
		String result = null;
		if (obj != null) {
			StringWriter stringWriter = new StringWriter();
			// Marshalling with encoding
			mctx.marshalDocument(obj, charset.name(), null, stringWriter);

			// if pretty print feature is activated
			if (prettyPrint) {
				result = normalize(stringWriter.toString());
			} else {
				result = stringWriter.toString();
			}
			stringWriter.close();
		}
		return result;
	}

	/**
	 * Deserialize a XML-string to an object
	 * 
	 * @param xml
	 * @return The de-serialized object
	 * @throws JiBXException
	 * @throws SAXException
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public T deserialize(String xml) throws JiBXException, SAXException,
			IOException {

		if (xml != null) {
			// Validation if schema was set
			if (schema != null) {
				StringReader reader = new StringReader(xml);
				// Validation
				javax.xml.validation.Validator validator = schema
						.newValidator();
				SAXSource sax = new SAXSource(new InputSource(reader));
				try {
					validator.validate(sax);
				} finally {
					if (reader != null) {
						reader.close();
					}

				}

			}
			IUnmarshallingContext lUnmarshallingContext = bFact
					.createUnmarshallingContext();

			// Unmarshalling
			return (T) lUnmarshallingContext.unmarshalDocument(
					new StringReader(xml), charset.name());
		}
		return null;
	}

	/**
	 * Normalize a XML string using dom trees
	 * 
	 * @param xml
	 * @return The normalized string
	 * @throws XMLNormalizationException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private String normalize(String xml) throws ParserConfigurationException,
			SAXException, IOException {
		if (xml != null) {
			// Get DOM-Document from XML-String
			Document doc = getDomDocument(xml);
			DOMImplementation domImplementation = doc.getImplementation();
			// Check Implementation Version, if it have pretty print feature
			if (domImplementation.hasFeature("LS", "3.0")
					&& domImplementation.hasFeature("Core", "2.0")) {
				DOMImplementationLS domImplementationLS = (DOMImplementationLS) domImplementation
						.getFeature("LS", "3.0");
				// Build Serializer
				LSSerializer lsSerializer = domImplementationLS
						.createLSSerializer();
				DOMConfiguration domConfiguration = lsSerializer.getDomConfig();
				// Pretty print
				if (domConfiguration.canSetParameter("format-pretty-print",
						Boolean.TRUE)) {
					domConfiguration.setParameter("format-pretty-print",
							Boolean.TRUE);
					LSOutput lsOutput = domImplementationLS.createLSOutput();
					lsOutput.setEncoding(charset.name());
					StringWriter stringWriter = new StringWriter();
					lsOutput.setCharacterStream(stringWriter);
					// Write pretty
					lsSerializer.write(doc, lsOutput);
					String result = stringWriter.toString();
					// Check line separator
					return normalizeToFileString(result);
				}
			}
		}
		return null;
	}

	/**
	 * Get a DOM Document from XML-String declared in constructor
	 * 
	 * @return null, if xml string is not declared
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private Document getDomDocument(String xml)
			throws ParserConfigurationException, SAXException, IOException {

		Document doc = null;
		if (xml != null) {
			// Build DOM-Document
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			// Anonymous class to suppress error messages
			builder.setErrorHandler(new ErrorHandler() {
				@Override
				public void warning(SAXParseException exception)
						throws SAXException {
				}

				@Override
				public void fatalError(SAXParseException exception)
						throws SAXException {
				}

				@Override
				public void error(SAXParseException exception)
						throws SAXException {
				}
			});
			doc = builder.parse(new InputSource(new StringReader(xml)));
		}

		return doc;
	}

	/**
	 * Normalize String for output.
	 * 
	 * @param in
	 * @return The normalized string
	 */
	private String normalizeToFileString(String in) {
		in = in.replaceAll("(\r\n|\n|\r)", System.getProperty("line.separator"));
		return in;
	}

}
