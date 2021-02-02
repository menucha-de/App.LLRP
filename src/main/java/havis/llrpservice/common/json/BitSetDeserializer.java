package havis.llrpservice.common.json;

import java.io.IOException;
import java.util.BitSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class BitSetDeserializer extends JsonDeserializer<BitSet> {

	@Override
	public BitSet deserialize(JsonParser arg0, DeserializationContext arg1)
			throws IOException, JsonProcessingException {

		String results = "";
		BitSet bitSet;

		results = hex2Binary(arg0.getValueAsString());

		bitSet = new BitSet(results.length());
		for (int i = 0; i < results.length(); i++) {
			if (results.charAt(i) == '1') {
				bitSet.set(i);
			}
		}
		return bitSet;
	}

	private static String hex2Binary(String hex) {
		StringBuffer binary = new StringBuffer();
		int len = hex.length();
		for (int i = 0; i < len; i++) {
			String hexChar = hex.substring(i, i + 1);
			int convInt = Integer.parseInt(hexChar, 16);
			String binChar = Integer.toBinaryString(convInt);
			binary.append(zeroPadBinChar(binChar));
		}
		return binary.toString();
	}

	private static String zeroPadBinChar(String binary) {
		int len = binary.length();
		if (len % 4 == 0) {
			return binary;
		}
		StringBuffer ret = new StringBuffer("0");
		for (int i = 1; i < 4 - len % 4; i++) {
			ret.append('0');
		}
		ret.append(binary);
		return ret.toString();
	}
}
