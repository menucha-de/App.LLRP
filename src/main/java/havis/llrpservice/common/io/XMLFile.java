package havis.llrpservice.common.io;

import havis.llrpservice.common.serializer.XMLSerializer;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.jibx.runtime.JiBXException;
import org.xml.sax.SAXException;

import com.rits.cloning.Cloner;

/**
 * The XMLFile class allows the access to the physical data storage. It is
 * possible to get and save the content of file directly. The content will be
 * given and taken as object from type T. Data will be stored and read in XML
 * format.
 * <p>
 * The content will be read from the latest path or from the initial path, if
 * file in latest path does not exists. Content will be saved in latest past
 * anyway.
 * </p>
 * <p>
 * To activate XML validation, set a schema ({@link #setSchema(Path)})
 * </p>
 * 
 */
public class XMLFile<T> {

	private static final Logger log = Logger.getLogger(XMLFile.class.getName());

	private Path latestPath;
	private Path initialPath;
	private XMLSerializer<T> serializer;
	private Charset encoding;
	private T configurationObject;

	/**
	 * Sets paths. Initialize an XMLSerializer with type T. The default encoding
	 * is UTF 8.
	 * 
	 * @param clazz
	 * @param initialPath
	 * @param latestPath
	 * @throws JiBXException
	 */
	public XMLFile(Class<T> clazz, Path initialPath, Path latestPath) throws JiBXException {
		this.initialPath = initialPath;
		this.latestPath = latestPath;
		serializer = new XMLSerializer<T>(clazz);
		encoding = StandardCharsets.UTF_8;
	}

	/**
	 * Activates XML validation.
	 * 
	 * @param xsdPath
	 * @throws SAXException
	 */
	public synchronized void setSchema(Path xsdPath) throws SAXException {
		serializer.setSchema(xsdPath.toFile());
	}

	/**
	 * Sets the encoding for serializer.
	 * 
	 * @param encoding
	 */
	public synchronized void setEncoding(Charset encoding) {
		this.encoding = encoding;
	}

	/**
	 * Returns the currently used path.
	 * 
	 * @return The path
	 * @throws FileNotFoundException
	 */
	public synchronized Path getPath() {
		if (latestPath != null) {
			return getLatestPath();
		}
		if (initialPath != null) {
			return getInitialPath();
		}
		return null;
	}

	/**
	 * Returns the initial path.
	 * 
	 * @return The path
	 * @throws FileNotFoundException
	 */
	public synchronized Path getInitialPath() {
		return initialPath;
	}

	/**
	 * Returns the latest path.
	 * 
	 * @return The path
	 */
	public synchronized Path getLatestPath() {
		return latestPath;
	}

	/**
	 * Sets a new latest path. Previously loaded XML file content is discarded.
	 * 
	 * @param latestPath
	 */
	public synchronized void setLatestPath(Path latestPath) {
		this.latestPath = latestPath;
		configurationObject = null;
	}

	/**
	 * Reads the physical file and deserializes the content as object from type
	 * {@code T}. The content of the file is cached. Each call of this method
	 * returns a clone of the file content.
	 * 
	 * @return The de-serialized object
	 * @throws IOException
	 * @throws JiBXException
	 * @throws SAXException
	 */
	public synchronized T getContent() throws IOException, JiBXException, SAXException {
		if (configurationObject == null) {
			Path filePath = null;
			Path jarContentPath = null;
			if (latestPath != null && Files.isRegularFile(latestPath)) {
				filePath = latestPath;
			} else if (initialPath != null) {
				jarContentPath = new PathHandler().getJARContentPath(initialPath);
				if (jarContentPath != null || Files.isRegularFile(initialPath)) {
					filePath = initialPath;
				}
			}
			if (filePath == null) {
				throw new FileNotFoundException(
						(initialPath == null ? latestPath : initialPath).toString());
			}
			byte[] encoded = null;
			if (jarContentPath != null) {
				InputStream is = getClass().getClassLoader()
						.getResourceAsStream(jarContentPath.toString());
				try {
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					int c = 0;
					while ((c = is.read()) != -1) {
						bos.write((char) c);
					}
					encoded = bos.toByteArray();
				} catch (Exception e) {
					throw new IOException("Cannot read file from JAR: " + filePath.toString(), e);
				}
			} else {
				encoded = Files.readAllBytes(filePath);
			}
			String content = encoding.decode(ByteBuffer.wrap(encoded)).toString();
			configurationObject = serializer.deserialize(content);
			if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO,
						"Content from file {0} with content type {1} ({2} bytes) has been read.",
						new Object[] { filePath.toString(),
								configurationObject.getClass().getName(), encoded.length });
			}
		}
		return new Cloner().deepClone(configurationObject);
	}

	/**
	 * Serialize the content from type {@code T} to data storage in XML format.
	 * 
	 * @param configurationObject
	 * @throws IOException
	 * @throws JiBXException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	public synchronized void save(T configurationObject)
			throws IOException, JiBXException, ParserConfigurationException, SAXException {
		// if directory does not exists then create it
		if (latestPath.getParent() != null) {
			latestPath.getParent().toFile().mkdirs();
		}
		// write data to physical storage
		String content = serializer.serialize(configurationObject);
		byte[] encoded = content.getBytes(encoding);
		Files.write(latestPath, encoded);
		this.configurationObject = configurationObject;
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO,
					"Content with content type {0} ({1} Bytes) has been written to file {2}.",
					new Object[] { configurationObject.getClass().getName(), encoded.length,
							latestPath.toString() });
		}
	}
}
