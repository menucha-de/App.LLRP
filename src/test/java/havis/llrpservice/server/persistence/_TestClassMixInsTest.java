package havis.llrpservice.server.persistence;

import havis.llrpservice.server.persistence._TestClassTest.Enumeration;

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

	public static abstract class InnerTestClassMixIn {
		public InnerTestClassMixIn(@JsonProperty("foo") String foo,
				@JsonProperty("bar") int bar) {
		}
	}

}
