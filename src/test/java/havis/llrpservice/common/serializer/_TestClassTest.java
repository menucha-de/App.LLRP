package havis.llrpservice.common.serializer;

import java.io.Serializable;


public class _TestClassTest implements Serializable {
	
	private static final long serialVersionUID = -8171701796320426457L;

	public static enum Enumeration {
		FIRST, SECOND, THIRD, FOURTH, FITH
	}
	
	private Enumeration enumeration;
	private _InnerTestClassTest inTest;
	private String abc;
	private int efg;

	public _TestClassTest(_InnerTestClassTest inTest, String abc, int efg,
			Enumeration enumeration) {
		this.inTest = inTest;
		this.abc = abc;
		this.efg = efg;
		this.enumeration = enumeration;
	}

	public _InnerTestClassTest getInTest() {
		return inTest;
	}

	public void setInTest(_InnerTestClassTest inTest) {
		this.inTest = inTest;
	}

	public String getAbc() {
		return abc;
	}

	public void setAbc(String foo) {
		this.abc = foo;
	}

	public int getEfg() {
		return efg;
	}

	public void setEfg(int bar) {
		this.efg = bar;
	}

	public boolean isTrue() {
		return true;
	}

	public String getNullString() {
		return null;
	}

	public Enumeration getEnumeration() {
		return enumeration;
	}

	public void setEnumreation(Enumeration enumeration) {
		this.enumeration = enumeration;
	}

	@Override
	public String toString() {
		return "TestClass [InnerTestClass=" + inTest + ", abc=" + abc
				+ ", efg=" + efg + ", enumeration= "
				+ (enumeration == null ? "null" : enumeration.name()) + "]";
	}

}
