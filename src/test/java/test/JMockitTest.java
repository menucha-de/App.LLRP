package test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Properties;

import org.testng.annotations.Test;

import mockit.Capturing;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.NonStrictExpectations;

/**
 * Tests for JMockit.
 * <p>
 * Detected changes from V1.8 to V1.17:
 * <ul>
 * <li>A mocked class cannot be mocked again.</li>
 * <li>Loops in verification blocks are not allowed (use
 * withCapture(List&lt;..&gt;) instead).</li>
 * <li>@Cascading was removed. @Mocked provides cascading feature by default
 * =&gt; mocked methods does NOT return <code>null</code> by default.</li>
 * <li>@Mocked for an interface only mocks the created mock instance
 * (use @Capturing instead)</li>
 * <li>Deencapsulation.setField: JMockit does not reset changed static fields at
 * the end of a test method =&gt; do it manually</li>
 * <li>The capturing of interfaces and abstract classes does not work if
 * implementing classes are created via reflection.</li>
 * </ul>
 * </p>
 */
@Test
public class JMockitTest {
	interface TestInterface {
		int getKey();
	}

	public abstract class TestAbstractClass implements TestInterface {
		int key;

		TestAbstractClass() {
			key = 3;
		}

		TestAbstractClass(int key) {
			this.key = key;
		}

		public int getKey() {
			return key;
		}

		public abstract int getValue();
	}

	class TestClass extends TestAbstractClass {

		int key;

		TestClass() {
			key = 3;
		}

		TestClass(int key) {
			this.key = key;
		}

		public int getKey() {
			return key;
		}

		public int getValue() {
			return key + 1;
		}

		public Properties getProperties() {
			return new Properties();
		}
	}

	// @Mocked
	// TestClass tc1;

	@Test
	public void class1(//
			@Mocked final TestClass tc1//
	) {
		// the whole class is mocked
		assertEquals(tc1.getKey(), 0);
		assertEquals(tc1.getValue(), 0);
		// a mocked instance is returned due to default cascading feature
		assertNotNull(tc1.getProperties());

		TestClass tc2 = new TestClass();
		assertEquals(tc2.getKey(), 0);
		assertEquals(tc2.getValue(), 0);

		TestClass tc3 = new TestClass(5);
		assertEquals(tc3.getKey(), 0);
		assertEquals(tc3.getValue(), 0);

		new Expectations() {
			{
				// redefine the mocked "getKey" method
				tc1.getKey();
				result = 11;
			}
		};
		// all existing instances have been modified
		assertEquals(tc1.getKey(), 11);
		assertEquals(tc1.getValue(), 0);
		assertEquals(tc2.getKey(), 11);
		assertEquals(tc2.getValue(), 0);
		assertEquals(tc3.getKey(), 11);
		assertEquals(tc3.getValue(), 0);

		// create new instances
		TestClass tc4 = new TestClass();
		TestClass tc5 = new TestClass();
		// all new instances have been modified
		assertEquals(tc4.getKey(), 11);
		assertEquals(tc4.getValue(), 0);
		assertEquals(tc5.getKey(), 11);
		assertEquals(tc5.getValue(), 0);

		new Expectations() {
			{
				// additionally redefine the mocked "getValue" method
				tc1.getValue();
				result = 22;
			}
		};
		// all existing instances have been modified
		assertEquals(tc1.getKey(), 11);
		assertEquals(tc1.getValue(), 22);
		assertEquals(tc2.getKey(), 11);
		assertEquals(tc2.getValue(), 22);
		assertEquals(tc3.getKey(), 11);
		assertEquals(tc3.getValue(), 22);
		assertEquals(tc4.getKey(), 11);
		assertEquals(tc4.getValue(), 22);
		assertEquals(tc5.getKey(), 11);
		assertEquals(tc5.getValue(), 22);

		// create new instances
		TestClass tc6 = new TestClass();
		TestClass tc7 = new TestClass(5);
		// all new instances have been modified
		assertEquals(tc6.getKey(), 11);
		assertEquals(tc6.getValue(), 22);
		assertEquals(tc7.getKey(), 11);
		assertEquals(tc7.getValue(), 22);
	}

	@Test
	public void partialClass1() {
		final TestClass tc2 = new TestClass();
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc2.getValue(), 4);

		final TestClass tc3 = new TestClass(5);
		assertEquals(tc3.getKey(), 5);
		assertEquals(tc3.getValue(), 6);

		new Expectations(TestClass.class) {
			{
				// mock the "getKey" method
				tc2.getKey();
				result = 11;
			}
		};

		// all existing instances have been modified
		assertEquals(tc2.getKey(), 11);
		assertEquals(tc2.getValue(), 4);
		assertEquals(tc3.getKey(), 11);
		assertEquals(tc3.getValue(), 6);

		// create new instances
		TestClass tc4 = new TestClass();
		TestClass tc5 = new TestClass(5);
		// all new instances have been modified
		assertEquals(tc4.getKey(), 11);
		assertEquals(tc4.getValue(), 4);
		assertEquals(tc5.getKey(), 11);
		assertEquals(tc5.getValue(), 6);

		new Expectations() {
			{
				// additionally mock the "getValue" method
				tc2.getValue();
				result = 12;
			}
		};
		// all existing instances have been modified
		assertEquals(tc2.getKey(), 11);
		assertEquals(tc2.getValue(), 12);
		assertEquals(tc3.getKey(), 11);
		assertEquals(tc3.getValue(), 12);
		assertEquals(tc4.getKey(), 11);
		assertEquals(tc4.getValue(), 12);
		assertEquals(tc5.getKey(), 11);
		assertEquals(tc5.getValue(), 12);

		// create new instances
		TestClass tc6 = new TestClass();
		TestClass tc7 = new TestClass(5);
		// all new instances have been modified
		assertEquals(tc6.getKey(), 11);
		assertEquals(tc6.getValue(), 12);
		assertEquals(tc7.getKey(), 11);
		assertEquals(tc7.getValue(), 12);
	}

	@Test
	public void partialClassMockUp1() {
		TestClass tc2 = new TestClass();
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc2.getValue(), 4);

		TestClass tc3 = new TestClass(5);
		assertEquals(tc3.getKey(), 5);
		assertEquals(tc3.getValue(), 6);

		new MockUp<TestClass>() {
			// only mock the "getKey" method
			@Mock
			int getKey() {
				return 11;
			}
		};
		// all existing instances have been modified
		assertEquals(tc2.getKey(), 11);
		assertEquals(tc2.getValue(), 4);
		assertEquals(tc3.getKey(), 11);
		assertEquals(tc3.getValue(), 6);

		// create new instances
		TestClass tc4 = new TestClass();
		TestClass tc5 = new TestClass(5);
		// all new instaces have been modified
		assertEquals(tc4.getKey(), 11);
		assertEquals(tc4.getValue(), 4);
		assertEquals(tc5.getKey(), 11);
		assertEquals(tc5.getValue(), 6);

		new MockUp<TestClass>() {
			// additionally mock the "getValue" method
			@Mock
			int getValue(Invocation inv) {
				int a = inv.proceed();
				return a + 10;
			}
		};
		// all existing instances have been modified
		assertEquals(tc2.getKey(), 11);
		assertEquals(tc2.getValue(), 14);
		assertEquals(tc3.getKey(), 11);
		assertEquals(tc3.getValue(), 16);
		assertEquals(tc4.getKey(), 11);
		assertEquals(tc4.getValue(), 14);
		assertEquals(tc5.getKey(), 11);
		assertEquals(tc5.getValue(), 16);

		// create new instances
		TestClass tc6 = new TestClass();
		TestClass tc7 = new TestClass(5);
		// all new instances have been modified
		assertEquals(tc6.getKey(), 11);
		assertEquals(tc6.getValue(), 14);
		assertEquals(tc7.getKey(), 11);
		assertEquals(tc7.getValue(), 16);
	}

	@Test
	public void partialClassConstructor1() {
		TestClass tc2 = new TestClass();
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc2.getValue(), 4);

		TestClass tc3 = new TestClass(5);
		assertEquals(tc3.getKey(), 5);
		assertEquals(tc3.getValue(), 6);

		new Expectations(TestClass.class) {
			{
				// mock the constructor without parameters
				TestClass tc1 = new TestClass();

				// mock the "getKey" method for all instances which will be
				// created via the mocked constructor (existing instances are
				// not modified if the constructor has been mocked)
				tc1.getKey();
				result = 11;
			}
		};
		// all existing instances have NOT been modified
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc2.getValue(), 4);
		assertEquals(tc3.getKey(), 5);
		assertEquals(tc3.getValue(), 6);

		// create new instances
		TestClass tc4 = new TestClass();
		TestClass tc5 = new TestClass(5);
		// only the instances with the mocked constructor has been modified
		assertEquals(tc4.getKey(), 11);
		assertEquals(tc4.getValue(), 1);
		assertEquals(tc5.getKey(), 5);
		assertEquals(tc5.getValue(), 6);

		// the mocked class cannot be mocked again
	}

	@Test
	public void partialClassInstance1() {
		final TestClass tc2 = new TestClass();
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc2.getValue(), 4);

		TestClass tc3 = new TestClass(5);
		assertEquals(tc3.getKey(), 5);
		assertEquals(tc3.getValue(), 6);

		new Expectations(tc2) {
			{
				// only mock the "getKey" method of one instance
				tc2.getKey();
				result = 11;
			}
		};
		// only the mocked instance has been modified
		assertEquals(tc2.getKey(), 11);
		assertEquals(tc2.getValue(), 4);
		assertEquals(tc3.getKey(), 5);
		assertEquals(tc3.getValue(), 6);

		// create new instances
		TestClass tc4 = new TestClass();
		TestClass tc5 = new TestClass(5);
		// the new instances have NOT been modified
		assertEquals(tc4.getKey(), 3);
		assertEquals(tc4.getValue(), 4);
		assertEquals(tc5.getKey(), 5);
		assertEquals(tc5.getValue(), 6);

		// the mocked class cannot be mocked again
	}

	// @Mocked
	// TestInterface tc0;

	@Test
	public void interface1(//
			@Mocked final TestInterface tc0//
	) {
		// a mock instance has been created
		assertEquals(tc0.getKey(), 0);

		TestClass tc1 = new TestClass();
		assertEquals(tc1.getKey(), 3);

		new Expectations() {
			{
				// redefine the mocked "getKey" method
				tc0.getKey();
				result = 10;
			}
		};
		// only the created mock instance has been modified
		assertEquals(tc0.getKey(), 10);
		assertEquals(tc1.getKey(), 3);

		// create new instances
		TestClass tc2 = new TestClass();
		TestAbstractClass tc3 = new TestAbstractClass() {

			@Override
			public int getKey() {
				return 7;
			}

			@Override
			public int getValue() {
				return 71;
			}
		};
		TestAbstractClass tc4 = new TestClass() {

			@Override
			public int getKey() {
				return 8;
			}
		};
		// all new instances have NOT been modified
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc3.getKey(), 7);
		assertEquals(tc4.getKey(), 8);
	}

	// @Capturing
	// TestInterface tc0;

	@Test
	public void interface2(//
			@Capturing final TestInterface tc0//
	) {
		// a mock instance has been created
		assertEquals(tc0.getKey(), 0);

		TestClass tc1 = new TestClass();
		assertEquals(tc1.getKey(), 0);

		new Expectations() {
			{
				// redefine the mocked "getKey" method
				tc0.getKey();
				result = 10;
			}
		};
		// all existing instances have been modified
		assertEquals(tc0.getKey(), 10);
		assertEquals(tc1.getKey(), 10);

		// create new instances
		TestClass tc2 = new TestClass();
		TestAbstractClass tc3 = new TestAbstractClass() {

			@Override
			public int getValue() {
				return 7;
			}
		};
		TestAbstractClass tc4 = new TestClass() {

			@Override
			public int getValue() {
				return 8;
			}
		};
		// all new instances have been modified
		assertEquals(tc2.getKey(), 10);
		assertEquals(tc3.getKey(), 10);
		assertEquals(tc4.getKey(), 10);
	}

	// @Capturing
	// TestInterface tc1;

	@Test
	public void partialInterface1(//
			@Capturing final TestInterface tc1//
	) throws Exception {
		// the whole class is mocked
		assertEquals(tc1.getKey(), 0);

		// Change the mocked class to a partial mocked one.
		// Without any expectation all methods are reset to their original
		// implementation.
		new Expectations(TestInterface.class) {
			{
			}
		};
		// the existing instances has NOT been modified
		assertEquals(tc1.getKey(), 0);

		// create new instances
		TestClass tc2 = new TestClass();
		TestInterface tc3 = new TestInterface() {

			@Override
			public int getKey() {
				return 7;
			}
		};
		TestInterface tc4 = new TestClass() {

			@Override
			public int getKey() {
				return 8;
			}
		};
		// all new instances have been modified: the original implementation has
		// been activated again
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc3.getKey(), 7);
		assertEquals(tc4.getKey(), 8);

		// add an expectation for the partially mocked class
		new Expectations(TestInterface.class) {
			{
				// mock the abstract method
				tc1.getKey();
				result = 9;
			}
		};
		// the existing instances have been modified
		assertEquals(tc2.getKey(), 9);
		assertEquals(tc3.getKey(), 9);
		assertEquals(tc4.getKey(), 9);

		// create new instances
		tc2 = new TestClass();
		tc3 = new TestAbstractClass() {

			@Override
			public int getValue() {
				return 7;
			}
		};
		tc4 = new TestClass() {

			@Override
			public int getValue() {
				return 8;
			}
		};
		// all new instances have been modified
		assertEquals(tc2.getKey(), 9);
		assertEquals(tc3.getKey(), 9);
		assertEquals(tc4.getKey(), 9);
	}

	// @Capturing
	// TestAbstractClass tc1;

	@Test
	public void abstractClass1(//
			@Capturing final TestAbstractClass tc1//
	) {
		// the whole class is mocked
		assertEquals(tc1.getKey(), 0);
		assertEquals(tc1.getValue(), 0);

		new Expectations() {
			{
				// redefine the mocked "getValue" method
				tc1.getValue();
				result = 10;
			}
		};
		// the existing instances have been modified
		assertEquals(tc1.getKey(), 0);
		assertEquals(tc1.getValue(), 10);

		// create new instances
		TestClass tc2 = new TestClass();
		TestAbstractClass tc3 = new TestAbstractClass() {

			@Override
			public int getValue() {
				return 7;
			}
		};
		TestAbstractClass tc4 = new TestClass() {

			@Override
			public int getValue() {
				return 8;
			}
		};
		// all new instances have been modified
		assertEquals(tc2.getValue(), 10);
		assertEquals(tc3.getValue(), 10);
		assertEquals(tc4.getValue(), 10);
	}

	// @Capturing TestAbstractClass tc1;

	@Test
	public void partialAbstractClass2(//
			@Capturing final TestAbstractClass tc1//
	) {
		// the whole class is mocked
		assertEquals(tc1.getKey(), 0);
		assertEquals(tc1.getValue(), 0);

		// Change the mocked class to a partial mocked one.
		// Without any expectation all methods are reset to their original
		// implementation.
		new NonStrictExpectations(TestAbstractClass.class) {
			{
			}
		};
		// the existing instances has NOT been modified
		assertEquals(tc1.getKey(), 0);
		assertEquals(tc1.getValue(), 0);

		// create new instances
		TestClass tc2 = new TestClass();
		TestAbstractClass tc3 = new TestAbstractClass() {

			@Override
			public int getValue() {
				return 7;
			}
		};
		TestAbstractClass tc4 = new TestClass() {

			@Override
			public int getValue() {
				return 8;
			}
		};
		// all new instances have been modified: the original implementation has
		// been activated again
		assertEquals(tc2.getValue(), 4);
		assertEquals(tc3.getValue(), 7);
		assertEquals(tc4.getValue(), 8);

		// add an expectation for the partially mocked class
		new NonStrictExpectations(TestAbstractClass.class) {
			{
				// mock the abstract method
				tc1.getValue();
				result = 9;
			}
		};
		// the existing instances have been modified
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc2.getValue(), 9);
		assertEquals(tc3.getKey(), 3);
		assertEquals(tc3.getValue(), 9);
		assertEquals(tc4.getKey(), 3);
		assertEquals(tc4.getValue(), 9);

		// create new instances
		tc2 = new TestClass();
		tc3 = new TestAbstractClass() {

			@Override
			public int getValue() {
				return 7;
			}
		};
		tc4 = new TestClass() {

			@Override
			public int getValue() {
				return 8;
			}
		};
		// all new instances have been modified
		assertEquals(tc2.getKey(), 3);
		assertEquals(tc2.getValue(), 9);
		assertEquals(tc3.getKey(), 3);
		assertEquals(tc3.getValue(), 9);
		assertEquals(tc4.getKey(), 3);
		assertEquals(tc4.getValue(), 9);
	}
}
