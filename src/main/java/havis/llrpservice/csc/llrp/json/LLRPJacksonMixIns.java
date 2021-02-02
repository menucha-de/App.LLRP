package havis.llrpservice.csc.llrp.json;

import havis.llrpservice.common.json.BitSetDeserializer;
import havis.llrpservice.common.json.BitSetSerializer;
import havis.llrpservice.common.json.ByteArrayDeserializer;
import havis.llrpservice.common.json.ByteArraySerializer;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.TVParameterHeader;

import java.util.BitSet;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class LLRPJacksonMixIns extends HashMap<Class<?>, Class<?>> {
	private static final long serialVersionUID = -3611453880174202885L;

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

	public LLRPJacksonMixIns() {
		put(TLVParameterHeader.class, TLVParameterHeaderMixIn.class);
		put(TVParameterHeader.class, TLVParameterHeaderMixIn.class);
		put(BitSet.class, BitSetMixIn.class);
		put(byte[].class, ByteArrayMixIn.class);
	}

}
