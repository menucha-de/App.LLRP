package havis.llrpservice.common.entityManager;

import havis.llrpservice.common.entityManager.FileEntityManager;
import havis.llrpservice.common.entityManager.MissingPropertyException;
import havis.llrpservice.common.entityManager.XMLFileEntityManager;
import havis.llrpservice.common.entityManager.FileEntityManager.FileProperty;
import havis.llrpservice.common.entityManager.XMLFileEntityManager.XmlProperty;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import havis.LLRP.generated.EnumerationType;
import havis.LLRP.generated.InnerClassType;
import havis.LLRP.generated.TestClassType;


public class XMLFileEntityManagerTest {

	private FileEntityManager<TestClassType> manager;
	private String outputDir = "output";
	private String version = "1.0";

	@BeforeClass
	public void init() {
		cleanUp();
	}
	
	@Test
	public void all() throws Exception {

		Map<FileProperty, Object> fileProperties = new HashMap<>();
		fileProperties.put(FileProperty.BASEDIR, outputDir);
		// Missing mandatory parameter ENCODING
		try {
			manager = new XMLFileEntityManager<>(TestClassType.class, version,
					fileProperties, new HashMap<XmlProperty, Object>());
			Assert.fail();
		} catch (MissingPropertyException e) {
			if (!e.getMessage().contains("ENCODING"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}

		Map<XmlProperty, Object> xmlProperties = new HashMap<>();
		xmlProperties.put(XmlProperty.ENCODING, StandardCharsets.UTF_8);

		// Test serializer without mix in (default serializer)
		manager = new XMLFileEntityManager<>(TestClassType.class, version,
				fileProperties, xmlProperties);

		InnerClassType inner = new InnerClassType();
		inner.setFoo("Hugo");
		inner.setBar(25);

		TestClassType outer = new TestClassType();
		outer.setInnerClass(inner);
		outer.setAbc("Max");
		outer.setEfg(31);
		outer.setEnumeration(EnumerationType.FIRST);

		manager.open();

		byte[] serialized = manager.serialize(outer);
		TestClassType compare = null;

		manager = new XMLFileEntityManager<>(TestClassType.class, version,
				fileProperties, xmlProperties);
		manager.open();

		// Serialize
		serialized = manager.serialize(outer);
		// Deserialize
		compare = manager.deserialize(serialized);
		// Compare original with deserialized object
		Assert.assertEquals(compare.getAbc(), outer.getAbc());

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
