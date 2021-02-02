package havis.llrpservice.common.serializer;

import java.io.Serializable;

public class _InnerTestClassTest implements Serializable{
	private static final long serialVersionUID = -3398865903628556882L;
	private String foo;
	private int bar;
	private String nullValue;

	public _InnerTestClassTest(String foo, int bar) {
		this.foo = foo;
		this.bar = bar;
		nullValue = null;
	}

	public String getFoo() {
		return foo;
	}

	public String getNullValue() {
		return nullValue;
	}

	public void setNullValue(String nullValue) {
		this.nullValue = nullValue;
	}

	@Override
	public String toString() {
		return "InnerTestClass [foo=" + foo + ", bar=" + bar + ", nullValue="
				+ nullValue + "]";
	}

}
