package havis.llrpservice.common.serializer;

import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.common.serializer._TestClassMixInsTest.InnerTestClassMixIn;
import havis.llrpservice.common.serializer._TestClassMixInsTest.TestClassDesMixIn;
import havis.llrpservice.common.serializer._TestClassMixInsTest.TestClassMixIn;
import havis.llrpservice.common.serializer._TestClassTest.Enumeration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

public class JsonSerializerTest {

	@Test
	public void serialize() throws JsonParseException, JsonMappingException,
			IOException {
		// Test serialization nesting
		JsonSerializer serializer = new JsonSerializer(_TestClassTest.class);
		serializer.setPrettyPrint(true);
		_InnerTestClassTest innerTestClass = new _InnerTestClassTest("foo", 842);
		_TestClassTest testClass = new _TestClassTest(innerTestClass, "abc",
				123, Enumeration.FIRST);

		serializer.setSerMapperFeature(
				MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

		/*
		 * { "abc" : "abc", "efg" : 123, "inTest" : { "foo" : "foo", "nullValue"
		 * : null }, "nullString" : null, "true" : true }
		 */

		// System.out.println(serializer.serialize(testClass));

		Assert.assertEquals(
				serializer.serialize(testClass),
				"{" + System.getProperty("line.separator")
						+ "  \"abc\" : \"abc\","
						+ System.getProperty("line.separator")
						+ "  \"efg\" : 123,"
						+ System.getProperty("line.separator")
						+ "  \"enumeration\" : \"FIRST\","
						+ System.getProperty("line.separator")
						+ "  \"inTest\" : {"
						+ System.getProperty("line.separator")
						+ "    \"foo\" : \"foo\","
						+ System.getProperty("line.separator")
						+ "    \"nullValue\" : null"
						+ System.getProperty("line.separator") + "  },"
						+ System.getProperty("line.separator")
						+ "  \"nullString\" : null,"
						+ System.getProperty("line.separator")
						+ "  \"true\" : true"
						+ System.getProperty("line.separator") + "}");

		// Test MixIn in serialization
		serializer = new JsonSerializer(_TestClassTest.class);
		serializer.setSerMapperFeature(
				MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

		serializer.setDesMapperFeature(
				MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

		Map<Class<?>, Class<?>> mixIns = new HashMap<Class<?>, Class<?>>();
		mixIns.put(_TestClassTest.class, TestClassMixIn.class);
		serializer.addSerializerMixIns(mixIns);
		Assert.assertEquals(
				serializer.serialize(testClass),
				"{" + "\"abc\":\"abc\",\"efg\":123,\"enumeration\":\"FIRST\",\"inTest\":{\"foo\":\"foo\",\"nullValue\":null},\"true\":true" + "}");
	}

	@Test
	public void deserialize() throws JsonParseException, JsonMappingException,
			IOException {
		JsonSerializer serializer = new JsonSerializer(_TestClassTest.class);
		_InnerTestClassTest innerTestClass = new _InnerTestClassTest("foo", 842);
		_TestClassTest testClass = new _TestClassTest(innerTestClass, "abc",
				123, Enumeration.SECOND);
		_TestClassTest result = null;
		// No MixIn class so no suitable constructor can be found
		try {
			result = serializer.deserialize("{"
					+ System.getProperty("line.separator")
					+ "  \"abc\" : \"abc\","
					+ System.getProperty("line.separator") + "  \"efg\" : 123,"
					+ System.getProperty("line.separator")
					+ "  \"enumeration\" : \"SECOND\","
					+ System.getProperty("line.separator") + "  \"inTest\" : {"
					+ System.getProperty("line.separator")
					+ "    \"bar\" : 842,"
					+ System.getProperty("line.separator")
					+ "    \"foo\" : \"foo\","
					+ System.getProperty("line.separator")
					+ "    \"nullValue\" : null"
					+ System.getProperty("line.separator") + "  },"
					+ System.getProperty("line.separator")
					+ "  \"nullString\" : null,"
					+ System.getProperty("line.separator")
					+ "  \"true\" : true"
					+ System.getProperty("line.separator") + "}");
			Assert.fail();
		} catch (JsonMappingException e) {

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

		// MixIn
		serializer = new JsonSerializer(_TestClassTest.class);
		Map<Class<?>, Class<?>> mixIns = new HashMap<Class<?>, Class<?>>();
		mixIns.put(_TestClassTest.class, TestClassDesMixIn.class);
		mixIns.put(_InnerTestClassTest.class, InnerTestClassMixIn.class);
		serializer.addSerializerMixIns(mixIns);
		serializer.addDeserializerMixIns(mixIns);
		result = serializer.deserialize("{"
				+ System.getProperty("line.separator") + "  \"abc\" : \"abc\","
				+ System.getProperty("line.separator") + "  \"efg\" : 123,"
				+ System.getProperty("line.separator")
				+ "  \"enumeration\" : \"SECOND\","
				+ System.getProperty("line.separator") + "  \"inTest\" : {"
				+ System.getProperty("line.separator") + "    \"bar\" : 842,"
				+ System.getProperty("line.separator")
				+ "    \"foo\" : \"foo\","
				+ System.getProperty("line.separator")
				+ "    \"nullValue\" : null"
				+ System.getProperty("line.separator") + "  },"
				+ System.getProperty("line.separator")
				+ "  \"nullString\" : null,"
				+ System.getProperty("line.separator") + "  \"true\" : true"
				+ System.getProperty("line.separator") + "}");
		// Object are equal
		Assert.assertEquals(result.toString(), testClass.toString());

		// Remove property (object)
		result = serializer.deserialize("{"
				+ System.getProperty("line.separator") + "  \"abc\" : null,"
				+ System.getProperty("line.separator") + "  \"efg\" : 123,"
				+ System.getProperty("line.separator")
				+ "  \"enumeration\" : \"SECOND\","
				+ System.getProperty("line.separator") + "  \"inTest\" : {"
				+ System.getProperty("line.separator") + "    \"bar\" : 842,"
				+ System.getProperty("line.separator")
				+ "    \"foo\" : \"foo\","
				+ System.getProperty("line.separator")
				+ "    \"nullValue\" : null"
				+ System.getProperty("line.separator") + "  },"
				+ System.getProperty("line.separator")
				+ "  \"nullString\" : null,"
				+ System.getProperty("line.separator") + "  \"true\" : true"
				+ System.getProperty("line.separator") + "}");
		testClass.setAbc(null);
		Assert.assertEquals(result.toString(), testClass.toString());

		// Remove property (integer)
		result = serializer.deserialize("{"
				+ System.getProperty("line.separator") + "  \"inTest\" : {"
				+ System.getProperty("line.separator") + "    \"bar\" : 842,"
				+ System.getProperty("line.separator")
				+ "    \"foo\" : \"foo\","
				+ System.getProperty("line.separator")
				+ "    \"nullValue\" : null"
				+ System.getProperty("line.separator") + "  },"
				+ System.getProperty("line.separator")
				+ "  \"nullString\" : null,"
				+ System.getProperty("line.separator") + "  \"true\" : true"
				+ System.getProperty("line.separator") + "}");
		testClass.setEfg(0);
		testClass.setEnumreation(null);
		Assert.assertEquals(result.toString(), testClass.toString());

		// Add additional property
		try {
			result = serializer.deserialize("{"
					+ System.getProperty("line.separator")
					+ "  \"oh\" : \"oh\","
					+ System.getProperty("line.separator")
					+ "  \"abc\" : \"abc\","
					+ System.getProperty("line.separator") + "  \"efg\" : 123,"
					+ System.getProperty("line.separator") + "  \"inTest\" : {"
					+ System.getProperty("line.separator")
					+ "    \"bar\" : 842,"
					+ System.getProperty("line.separator")
					+ "    \"foo\" : \"foo\","
					+ System.getProperty("line.separator")
					+ "    \"nullValue\" : null"
					+ System.getProperty("line.separator") + "  },"
					+ System.getProperty("line.separator")
					+ "  \"nullString\" : null,"
					+ System.getProperty("line.separator")
					+ "  \"true\" : true"
					+ System.getProperty("line.separator") + "}");
			Assert.fail();
		} catch (UnrecognizedPropertyException e) {

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}
	}
}
