package havis.llrpservice.common.io;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jibx.runtime.JiBXException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xml.sax.SAXParseException;

import havis.LLRP.generated.TestClassType;
import havis.llrpservice.common.entityManager._FileHelperTest;

public class XMLFileTest {

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/common/io/xmlFile");
	private static final Path INIT_PATH = BASE_PATH.resolve("TestClass.xml");
	private static final Path INIT_FAIL_PATH = BASE_PATH.resolve("TestClassFail.xml");
	private static final Path XSD_PATH = BASE_PATH.resolve("TestClass.xsd");

	private static String JAR_CONTENT_DIR = "havis/llrpservice/data";
	private static String JAR_CONTENT_FILE = "havis/llrpservice/data/DataTypeConverter.class";

	private static final Path BASE_OUTPUT_PATH = Paths.get("output").toAbsolutePath();
	private static final Path LATEST_PATH = BASE_OUTPUT_PATH.resolve("newTest.xml");
	private static final Path LATEST_FAIL_PATH = BASE_OUTPUT_PATH.resolve("failTest.xml");

	@BeforeClass
	public void init() throws IOException, URISyntaxException {
		cleanUp();
	}

	@Test
	public void fileContent() throws Exception {
		// initial path is null and file for latest path does NOT exist
		XMLFile<TestClassType> testFile = new XMLFile<>(TestClassType.class, null /* initPath */,
				LATEST_PATH);
		assertNull(testFile.getInitialPath());
		assertEquals(LATEST_PATH, testFile.getLatestPath());
		assertEquals(LATEST_PATH, testFile.getPath());
		// an exception is thrown
		try {
			testFile.getContent();
			fail();
		} catch (FileNotFoundException e) {
			assertTrue(e.getMessage().contains(LATEST_PATH.toString()));
		}

		// file for initial path exists and file for latest path does NOT exist
		testFile = new XMLFile<>(TestClassType.class, INIT_PATH, LATEST_PATH);
		assertEquals(INIT_PATH, testFile.getInitialPath());
		assertEquals(LATEST_PATH, testFile.getLatestPath());
		assertEquals(LATEST_PATH, testFile.getPath());
		// get file content
		TestClassType compare = testFile.getContent();

		compare.setEfg(25);
		// save changed content (latest path is created and exists up to now)
		testFile.save(compare);

		// file for initial path does NOT exists but latest path exists
		testFile = new XMLFile<>(TestClassType.class, INIT_PATH.resolve("a"), LATEST_PATH);
		// get file content twice and check if they have different references
		// (must be copies)
		compare = testFile.getContent();
		TestClassType copy = testFile.getContent();
		assertNotEquals(compare, copy);
		// the changed file has been read
		assertEquals(compare.getEfg(), 25);

		// initial path does NOT exists and latest path is null
		Path invalidInitPath = INIT_PATH.resolve("a");
		testFile = new XMLFile<>(TestClassType.class, invalidInitPath, null /* latestPath */);
		assertEquals(invalidInitPath, testFile.getInitialPath());
		assertNull(testFile.getLatestPath());
		assertEquals(invalidInitPath, testFile.getPath());
		// an exception is thrown
		try {
			compare = testFile.getContent();
			fail();
		} catch (FileNotFoundException e) {
			assertTrue(e.getMessage().contains(invalidInitPath.toString()));
		}

		// initial and latest path exists (latest path is used)
		testFile = new XMLFile<>(TestClassType.class, INIT_PATH, LATEST_PATH);
		// set XSD for validation
		testFile.setSchema(XSD_PATH);
		// get file content
		compare = testFile.getContent();

		// initial path exists with invalid file content
		// latest path does NOT exist
		testFile = new XMLFile<>(TestClassType.class, INIT_FAIL_PATH, LATEST_FAIL_PATH);
		testFile.setSchema(XSD_PATH);
		// an exception is thrown
		try {
			compare = testFile.getContent();
			fail();
		} catch (SAXParseException e) {
			assertTrue(e.getMessage().contains("foobar"));
		}
		// save valid content (latest path is created and exists up to now)
		testFile.save(compare);

		// get file content (latest path is used)
		compare = testFile.getContent();
		assertEquals(compare.getEfg(), 25);

		// check encoding
		testFile = new XMLFile<>(TestClassType.class, INIT_PATH, LATEST_PATH);
		compare = testFile.getContent();
		String content = "äöüß";
		compare.setAbc(content);
		testFile.setEncoding(StandardCharsets.ISO_8859_1);
		testFile.save(compare);

		testFile = new XMLFile<>(TestClassType.class, INIT_PATH, LATEST_PATH);
		compare = testFile.getContent();
		assertNotEquals(compare.getAbc(), content);
		testFile = new XMLFile<>(TestClassType.class, INIT_PATH, LATEST_PATH);
		testFile.setEncoding(StandardCharsets.ISO_8859_1);
		compare = testFile.getContent();
		assertEquals(compare.getAbc(), content);
	}

	@Test
	public void jarContent() throws Exception {
		// read an existing file from a JAR
		Path jarContentPath = new PathHandler().toAbsolutePath(JAR_CONTENT_FILE);
		XMLFile<TestClassType> testFile = new XMLFile<>(TestClassType.class, jarContentPath,
				null /* latestPath */);
		// an exception is thrown because it is not an XML file
		try {
			testFile.getContent();
			fail();
		} catch (JiBXException e) {
			assertTrue(e.getMessage().contains("Error parsing document"));
		}

		// try to read from a directory from a JAR
		jarContentPath = new PathHandler().toAbsolutePath(JAR_CONTENT_DIR);
		testFile = new XMLFile<>(TestClassType.class, jarContentPath, null /* latestPath */);
		// an exception is thrown
		try {
			testFile.getContent();
			fail();
		} catch (Exception e) {
			e.printStackTrace();
			assertTrue(e.getMessage().contains("Error accessing document"));
		}
	}

	@AfterClass
	public void cleanUp() {
		// Remove output directory
		try {
			_FileHelperTest.deleteFiles(BASE_OUTPUT_PATH.toString());
			BASE_OUTPUT_PATH.toFile().delete();
		} catch (Exception e) {
		}
	}

}
