package havis.llrpservice.server.persistence;

import havis.llrpservice.server.persistence._TestClassMixInsTest.InnerTestClassMixIn;
import havis.llrpservice.server.persistence._TestClassMixInsTest.TestClassMixIn;

import java.util.HashMap;

public class _JacksonMixInsTest extends HashMap<Class<?>, Class<?>> {
	private static final long serialVersionUID = 2468878780229014262L;

	public _JacksonMixInsTest() {
		put(_TestClassTest.class, TestClassMixIn.class);
		put(_InnerTestClassTest.class, InnerTestClassMixIn.class);
	}

}
