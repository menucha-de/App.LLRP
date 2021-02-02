package havis.llrpservice.common.json;

import java.io.IOException;
import java.util.BitSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class BitSetSerializer extends JsonSerializer<BitSet> {

	@Override
	public void serialize(BitSet bitSet, JsonGenerator arg1,
			SerializerProvider arg2) throws IOException,
			JsonProcessingException {

		StringBuilder builder = new StringBuilder();
		String results = "";

		int bitCount = bitSet.length();
		if (bitCount % 4 != 0) {
			bitCount += 4 - bitCount % 4;
		}
		for (int i = 0; i < bitCount; i++) {
			builder.append(bitSet.get(i) ? "1" : "0");
		}

		results = binary_to_hex(builder.toString());
		arg1.writeString(results);
	}

	private static String binary_to_hex(String binary) {
		StringBuilder hex = new StringBuilder();
		int len = binary.length() / 4;
		for (int i = 0; i < len; i++) {
			String binChar = binary.substring(4 * i, 4 * i + 4);
			int convInt = Integer.parseInt(binChar, 2);
			hex.append(Integer.toHexString(convInt));
		}
		return hex.toString();
	}
}
