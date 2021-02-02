package havis.llrpservice.sbc.gpio.json;

import havis.llrpservice.common.json.ByteArraySerializer;
import havis.llrpservice.common.json.ByteArrayDeserializer;

import java.util.HashMap;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class GPIOJacksonMixIns extends HashMap<Class<?>, Class<?>> {

	private static final long serialVersionUID = 3418869779477759179L;

	@JsonSerialize(using = ByteArraySerializer.class)
	@JsonDeserialize(using = ByteArrayDeserializer.class)
	static abstract class ByteArrayMixIn {

	}

	public GPIOJacksonMixIns() {
		put(byte[].class, ByteArrayMixIn.class);
	}

}
