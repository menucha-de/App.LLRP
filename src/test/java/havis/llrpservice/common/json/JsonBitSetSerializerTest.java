package havis.llrpservice.common.json;

import havis.llrpservice.common.serializer.JsonSerializer;

import java.io.IOException;
import java.util.BitSet;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class JsonBitSetSerializerTest {

	@Test
	public void test() throws IOException {
		// bitSet1=10101
		BitSet bitSet1 = new BitSet(7);
		bitSet1.set(0);
		bitSet1.set(2);
		bitSet1.set(4);

		// bitSet2=0011
		BitSet bitSet2 = new BitSet(4);
		bitSet2.set(2);
		bitSet2.set(3);

		BitSet bitSet3 = new BitSet();

		BitSet bitSet4 = null;

		JsonSerializer jsonSerializer = new JsonSerializer(BitSet.class);
		jsonSerializer.addSerializerMixIns(new JacksonMixIns());
		jsonSerializer.addDeserializerMixIns(new JacksonMixIns());

		String result = jsonSerializer.serialize(bitSet1);
		Assert.assertEquals(result, "\"a8\"");
		BitSet compareBitSet = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareBitSet, bitSet1);

		result = jsonSerializer.serialize(bitSet2);
		Assert.assertEquals(result, "\"3\"");
		compareBitSet = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareBitSet, bitSet2);

		result = jsonSerializer.serialize(bitSet3);
		Assert.assertEquals(result, "\"\"");
		compareBitSet = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareBitSet, bitSet3);

		result = jsonSerializer.serialize(bitSet4);
		Assert.assertEquals(result, "null");
		compareBitSet = jsonSerializer.deserialize(result);
		Assert.assertEquals(compareBitSet, bitSet4);
	}
}
