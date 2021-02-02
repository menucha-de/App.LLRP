package havis.llrpservice.common.serializer;

import havis.llrpservice.common.serializer._TestClassTest.Enumeration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class _TestClassMixInsTest {
	
	public static abstract class TestClassMixIn {
		TestClassMixIn(@JsonProperty("inTest") _InnerTestClassTest inTest,
				@JsonProperty("abc") String abc, @JsonProperty("efg") int efg,
				@JsonProperty("enumeration") Enumeration enumeration) {
		}

		@JsonIgnore
		abstract String getNullString();
	}

	public static abstract class TestClassDesMixIn {
		public TestClassDesMixIn(@JsonProperty("inTest") _InnerTestClassTest inTest,
				@JsonProperty("abc") String abc, @JsonProperty("efg") int efg,
				@JsonProperty("enumeration") Enumeration enumeration) {
		}

		@JsonIgnore
		abstract String getNullString();

		@JsonIgnore
		abstract boolean isTrue();
	}

	public static abstract class InnerTestClassMixIn {
		public InnerTestClassMixIn(@JsonProperty("foo") String foo,
				@JsonProperty("bar") int bar) {
		}
	}
	
}
