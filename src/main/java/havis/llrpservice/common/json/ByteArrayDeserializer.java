package havis.llrpservice.common.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

	@Override
	public byte[] deserialize(JsonParser arg0, DeserializationContext arg1)
			throws IOException, JsonProcessingException {

		return hexToBytes(arg0.getValueAsString());
	}

	private static byte[] hexToBytes(String hexStr)
			throws IllegalArgumentException {
		hexStr = hexStr.replaceAll("\\s|_", "");
		if (hexStr.length() % 2 != 0)
			throw new IllegalArgumentException(
					"Hex string must have an even number of characters.");

		byte[] result = new byte[hexStr.length() / 2];
		for (int i = 0; i < hexStr.length(); i += 2)
			result[i / 2] = Integer.decode(
					"0x" + hexStr.charAt(i) + hexStr.charAt(i + 1)).byteValue();

		return result;
	}
}
