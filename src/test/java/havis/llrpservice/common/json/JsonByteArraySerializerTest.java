package havis.llrpservice.common.json;

import havis.llrpservice.common.serializer.JsonSerializer;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class JsonByteArraySerializerTest {

	@Test
	public void test() throws IOException {
		byte[] testArray1 = { (byte) 0x0A, (byte) 0xFB };
		byte[] testArray2 = { (byte) 0x0A };
		byte[] testArray3 = {};
		byte[] testArray4 = null;

		JsonSerializer jsonSerializer = new JsonSerializer(byte[].class);
		jsonSerializer.addSerializerMixIns(new JacksonMixIns());
		jsonSerializer.addDeserializerMixIns(new JacksonMixIns());

		String result = jsonSerializer.serialize(testArray1);
		Assert.assertEquals(result, "\"0afb\"");
		byte[] compareArray = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareArray, testArray1);

		result = jsonSerializer.serialize(testArray2);
		Assert.assertEquals(result, "\"0a\"");
		compareArray = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareArray, testArray2);

		result = jsonSerializer.serialize(testArray3);
		Assert.assertEquals(result, "\"\"");
		compareArray = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareArray, testArray3);

		result = jsonSerializer.serialize(testArray4);
		Assert.assertEquals(result, "null");
		compareArray = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareArray, testArray4);
	}
}
