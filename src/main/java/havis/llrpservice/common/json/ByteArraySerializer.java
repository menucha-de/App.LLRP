package havis.llrpservice.common.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class ByteArraySerializer extends JsonSerializer<byte[]> {

	@Override
	public void serialize(byte[] arg0, JsonGenerator arg1,
			SerializerProvider arg2) throws IOException,
			JsonProcessingException {

		arg1.writeString(bytesToHex(arg0));
	}

	private static String bytesToHex(byte[] bytes) {
		if (bytes == null)
			return null;

		char[] hexChars = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'a', 'b', 'c', 'd', 'e', 'f' };
		char[] resChars = new char[bytes.length * 2];

		for (int iByte = 0; iByte < bytes.length; iByte++) {
			byte b = bytes[iByte];
			int b0 = (b & 0xf0) >> 4;
			int b1 = b & 0x0f;

			resChars[2 * iByte] = hexChars[b0];
			resChars[2 * iByte + 1] = hexChars[b1];
		}
		return new String(resChars);
	}
}
