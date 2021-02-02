package havis.llrpservice.sbc.rfc.json;

import havis.llrpservice.common.json.ByteArraySerializer;
import havis.llrpservice.common.json.ByteArrayDeserializer;

import java.util.HashMap;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class RFCJacksonMixIns extends HashMap<Class<?>, Class<?>> {
	private static final long serialVersionUID = 3796782242227224516L;

	@JsonSerialize(using = ByteArraySerializer.class)
	@JsonDeserialize(using = ByteArrayDeserializer.class)
	static abstract class ByteArrayMixIn {

	}

	public RFCJacksonMixIns() {
		put(byte[].class, ByteArrayMixIn.class);
	}

}
