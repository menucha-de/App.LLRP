package havis.llrpservice.common.serializer;

import havis.llrpservice.common.serializer.XMLSerializer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.ParserConfigurationException;

import org.jibx.runtime.JiBXException;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import havis.LLRP.generated.EnumerationType;
import havis.LLRP.generated.InnerClassType;
import havis.LLRP.generated.TestClassType;

public class XMLSerializerTest {

	InnerClassType inner;
	TestClassType outer;

	public XMLSerializerTest() {
		inner = new InnerClassType();
		inner.setFoo("Hugo");
		inner.setBar(25);

		outer = new TestClassType();
		outer.setInnerClass(inner);
		outer.setAbc("Max");
		outer.setEfg(31);
		outer.setEnumeration(EnumerationType.FIRST);
	}

	@Test
	public void serialize() throws JiBXException, IOException,
			ParserConfigurationException, SAXException {
		XMLSerializer<TestClassType> serializer = new XMLSerializer<>(
				TestClassType.class);
		serializer.setPrettyPrint(true);

		InnerClassType inner = new InnerClassType();
		inner.setFoo("Hugo");
		inner.setBar(25);

		TestClassType outer = new TestClassType();
		outer.setInnerClass(inner);
		outer.setAbc("Max");
		outer.setEfg(31);
		outer.setEnumeration(EnumerationType.FIRST);

		String result = serializer.serialize(outer);
		Assert.assertEquals(
				result,
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
						+ System.getProperty("line.separator")
						+ "<TestClass xmlns=\"urn:havis:llrp:server:configuration:xsd:1\">"
						+ System.getProperty("line.separator")
						+ "    <abc>Max</abc>"
						+ System.getProperty("line.separator")
						+ "    <efg>31</efg>"
						+ System.getProperty("line.separator")
						+ "    <innerClass>"
						+ System.getProperty("line.separator")
						+ "        <foo>Hugo</foo>"
						+ System.getProperty("line.separator")
						+ "        <bar>25</bar>"
						+ System.getProperty("line.separator")
						+ "    </innerClass>"
						+ System.getProperty("line.separator")
						+ "    <enumeration>FIRST</enumeration>"
						+ System.getProperty("line.separator") + "</TestClass>"
						+ System.getProperty("line.separator"));

		serializer.setPrettyPrint(false);
		serializer.setCharset(StandardCharsets.UTF_8);
		result = serializer.serialize(outer);

		Assert.assertEquals(
				result,
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?><TestClass xmlns=\"urn:havis:llrp:server:configuration:xsd:1\"><abc>Max</abc><efg>31</efg><innerClass><foo>Hugo</foo><bar>25</bar></innerClass><enumeration>FIRST</enumeration></TestClass>");

		result = serializer.serialize(null);
		Assert.assertNull(result);
	}

	@Test
	public void deserialize() throws JiBXException, SAXException, IOException,
			URISyntaxException {
		XMLSerializer<TestClassType> serializer = new XMLSerializer<>(
				TestClassType.class);

		TestClassType actual = serializer.deserialize(null);

		Assert.assertNull(actual);

		actual = serializer
				.deserialize("<?xml version=\"1.0\" encoding=\"UTF-8\"?><TestClass xmlns=\"urn:havis:llrp:server:configuration:xsd:1\"><abc>Max</abc><efg>31</efg><innerClass><foo>Hugo</foo><bar>25</bar></innerClass><enumeration>FIRST</enumeration></TestClass>");

		compare(actual, outer);

		actual = serializer
				.deserialize("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
						+ System.getProperty("line.separator")
						+ "<TestClass xmlns=\"urn:havis:llrp:server:configuration:xsd:1\">"
						+ System.getProperty("line.separator")
						+ "    <abc>Max</abc>"
						+ System.getProperty("line.separator")
						+ "    <efg>31</efg>"
						+ System.getProperty("line.separator")
						+ "    <innerClass>"
						+ System.getProperty("line.separator")
						+ "        <foo>Hugo</foo>"
						+ System.getProperty("line.separator")
						+ "        <bar>25</bar>"
						+ System.getProperty("line.separator")
						+ "    </innerClass>"
						+ System.getProperty("line.separator")
						+ "    <enumeration>FIRST</enumeration>"
						+ System.getProperty("line.separator") + "</TestClass>"
						+ System.getProperty("line.separator"));
		compare(actual, outer);

		URL xsdUrl = getClass().getResource(
				"/havis/llrpservice/common/serializer/TestClass.xsd");
		File xsdFile = new File(xsdUrl.toURI());
		serializer.setSchema(xsdFile);

		actual = serializer
				.deserialize("<?xml version=\"1.0\" encoding=\"UTF-8\"?><TestClass xmlns=\"urn:havis:llrp:server:configuration:xsd:1\"><abc>Max</abc><efg>31</efg><innerClass><foo>Hugo</foo><bar>25</bar></innerClass><enumeration>FIRST</enumeration></TestClass>");
		compare(actual, outer);

		try {
			actual = serializer
					.deserialize("<?xml version=\"1.0\" encoding=\"UTF-8\"?><TestClass xmlns=\"urn:havis:llrp:server:configuration:xsd:1\"><efg>31</efg><innerClass><foo>Hugo</foo><bar>25</bar></innerClass><enumeration>FIRST</enumeration></TestClass>");
			Assert.fail();
		} catch (SAXParseException e) {
			if (!e.getMessage().contains("abc"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}

		serializer.setSchema(null);
		try {
			actual = serializer
					.deserialize("<?xml version=\"1.0\" encoding=\"UTF-8\"?><TestClass xmlns=\"urn:havis:llrp:server:configuration:xsd:1\"><efg>31</efg><innerClass><foo>Hugo</foo><bar>25</bar></innerClass><enumeration>FIRST</enumeration></TestClass>");
			Assert.fail();
		} catch (JiBXException e) {
			if (!e.getMessage().contains("abc"))
				Assert.fail();
		} catch (Exception e) {
			Assert.fail();
		}

	}

	private void compare(TestClassType actual, TestClassType expected) {

		Assert.assertEquals(actual.getAbc(), expected.getAbc());
		Assert.assertEquals(actual.getEfg(), expected.getEfg());
		Assert.assertEquals(actual.getEnumeration(), expected.getEnumeration());
		Assert.assertEquals(actual.getInnerClass().getFoo(), expected
				.getInnerClass().getFoo());
		Assert.assertEquals(actual.getInnerClass().getBar(), expected
				.getInnerClass().getBar());

	}

}
