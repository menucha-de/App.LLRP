package havis.llrpservice.common.entityManager;

import havis.llrpservice.common.entityManager.FileEntityManager;
import havis.llrpservice.common.entityManager.JSONFileEntityManager;
import havis.llrpservice.common.entityManager.MissingPropertyException;
import havis.llrpservice.common.entityManager.FileEntityManager.FileProperty;
import havis.llrpservice.common.entityManager.JSONFileEntityManager.JsonProperty;
import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.common.serializer._InnerTestClassTest;
import havis.llrpservice.common.serializer._TestClassMixInsTest.InnerTestClassMixIn;
import havis.llrpservice.common.serializer._TestClassMixInsTest.TestClassDesMixIn;
import havis.llrpservice.common.serializer._TestClassTest;
import havis.llrpservice.common.serializer._TestClassTest.Enumeration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonMappingException;

public class JSONFileEntityManagerTest {

	private FileEntityManager<_TestClassTest> manager;
	private String outputDir = "output";
	private String version = "1.0";

	@BeforeClass
	public void init() throws IOException {
		cleanUp();
	}

	@Test
	public void all() throws Exception {

		Map<FileProperty, Object> fileProperties = new HashMap<>();
		fileProperties.put(FileProperty.BASEDIR, outputDir);
		// Missing mandatory parameter ENCODING
		try {
			manager = new JSONFileEntityManager<>(_TestClassTest.class,
					version, fileProperties,
					new HashMap<JsonProperty, Object>());
			Assert.fail();
		} catch (MissingPropertyException e) {
			if (!e.getMessage().contains("ENCODING"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}

		Map<JsonProperty, Object> jsonProperties = new HashMap<>();
		jsonProperties.put(JsonProperty.ENCODING, StandardCharsets.UTF_8);

		// Test serializer without mix in (default serializer)
		manager = new JSONFileEntityManager<>(_TestClassTest.class, version,
				fileProperties, jsonProperties);

		_InnerTestClassTest inner = new _InnerTestClassTest("Hugo", 0);
		_TestClassTest outer = new _TestClassTest(inner, "Max", 31,
				Enumeration.FIRST);

		manager.open();

		byte[] serialized = manager.serialize(outer);
		_TestClassTest compare = null;

		try {
			compare = manager.deserialize(serialized);
			Assert.fail();
		} catch (JsonMappingException e) {
			if (!e.getMessage().contains("_TestClassTest"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}

		// Define serializer and put it in manager
		JsonSerializer serializer = new JsonSerializer(_TestClassTest.class);
		Map<Class<?>, Class<?>> mixIns = new HashMap<Class<?>, Class<?>>();
		mixIns.put(_TestClassTest.class, TestClassDesMixIn.class);
		mixIns.put(_InnerTestClassTest.class, InnerTestClassMixIn.class);
		serializer.addDeserializerMixIns(mixIns);
		jsonProperties.put(JsonProperty.SERIALIZER, serializer);

		manager = new JSONFileEntityManager<>(_TestClassTest.class, version,
				fileProperties, jsonProperties);
		manager.open();

		// Serialize
		serialized = manager.serialize(outer);
		// Deserialize
		compare = manager.deserialize(serialized);
		// Compare original with deserialized object
		Assert.assertEquals(compare.toString(), outer.toString());

		manager.close();

		cleanUp();

	}
	
	@AfterClass
	public void cleanUp() {
		// Remove output directory
		try {
			_FileHelperTest.deleteFiles(outputDir);
			new File(outputDir).delete();
		} catch (Exception e) {
		}
	}
	
}
