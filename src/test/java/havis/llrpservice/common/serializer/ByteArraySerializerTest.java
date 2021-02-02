package havis.llrpservice.common.serializer;

import havis.llrpservice.common.serializer.ByteArraySerializer;

import java.io.IOException;
import java.io.Serializable;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ByteArraySerializerTest implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8304718613361615777L;

	class testClass implements Serializable {
		private static final long serialVersionUID = -3714710013041411149L;
		private String a;
		private int b;

		public testClass(String a, int b) {
			this.a = a;
			this.b = b;
		}

		public String getA() {
			return a;
		}

		public int getB() {
			return b;
		}

	}

	@Test
	public void test() throws IOException, ClassNotFoundException {
		testClass myClass = new testClass("Oh", 2);
		ByteArraySerializer serializer = new ByteArraySerializer();
		byte[] test = serializer.serialize(myClass);
		testClass compClass = new testClass("Oh", 2);
		testClass resultClass = serializer.deserialize(test);	
		Assert.assertEquals(resultClass.getA(), compClass.getA());
		Assert.assertEquals(resultClass.getB(), compClass.getB());
	}
}
