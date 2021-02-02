package havis.llrpservice.common.entityManager;

import havis.llrpservice.common.entityManager.FileEntityManager;
import havis.llrpservice.common.entityManager.JavaBinaryFileEntityManager;
import havis.llrpservice.common.entityManager.FileEntityManager.FileProperty;
import havis.llrpservice.common.serializer._InnerTestClassTest;
import havis.llrpservice.common.serializer._TestClassTest;
import havis.llrpservice.common.serializer._TestClassTest.Enumeration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class JavaBinaryFileEntityManagerTest {

	private FileEntityManager<_TestClassTest> manager;
	private String outputDir = "output";
	private String version = "1.0";

	@BeforeClass
	public void init() {
		cleanUp();
	}

	@Test
	public void all() throws Exception {

		Map<FileProperty, Object> properties = new HashMap<>();
		properties.put(FileProperty.BASEDIR, outputDir);
		manager = new JavaBinaryFileEntityManager<>(_TestClassTest.class,
				version, properties);
		manager.open();

		_InnerTestClassTest inner = new _InnerTestClassTest("Hugo", 42);
		_TestClassTest outer = new _TestClassTest(inner, "Max", 31,
				Enumeration.FIRST);

		// Serialize
		byte[] serialized = manager.serialize(outer);
		// Deserialize
		_TestClassTest compare = manager.deserialize(serialized);

		// Compare serialized object with original
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
