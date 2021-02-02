package havis.llrpservice.common.json;

import java.util.BitSet;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

class JacksonMixIns extends HashMap<Class<?>, Class<?>> {
	private static final long serialVersionUID = -7180668937634536087L;

	@JsonSerialize(using = BitSetSerializer.class)
	@JsonDeserialize(using = BitSetDeserializer.class)
	static abstract class BitSetMixIn {

	}

	@JsonSerialize(using = ByteArraySerializer.class)
	@JsonDeserialize(using = ByteArrayDeserializer.class)
	static abstract class ByteArrayMixIn {

	}

	static abstract class TLVParameterHeaderMixIn {
		@JsonIgnore
		abstract boolean isTLV();
	}

	public JacksonMixIns() {
		put(BitSet.class, BitSetMixIn.class);
		put(byte[].class, ByteArrayMixIn.class);
	}

}
