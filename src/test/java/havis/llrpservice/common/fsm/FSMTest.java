package havis.llrpservice.common.fsm;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.annotations.Test;

import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Mocked;
import mockit.Verifications;

public class FSMTest {

	class Event {
		private final String name;

		public Event(String name) {
			this.name = name;
		}

		public String toString() {
			return name;
		}
	}

	// @Mocked
	// Logger log;

	@Test
	public void parent(//
			@Mocked final Logger log//
	) throws Exception {
		new Expectations() {
			{
				log.isLoggable(Level.FINE);
				result = true;
			}
		};
		// create FSM:
		// s1 e1 t1 s2/s2.1
		// s2/s2.1 e1 t1 s1
		State<Event> s2 = new State<>("s2");
		State<Event> s21 = new State<>("s2.1", s2);
		Event e1 = new Event("e1");
		Transition<Event> t1 = new Transition<>("t1");
		State<Event> s1 = new State<>("s1", e1, t1, s21);
		s21.addConnection(e1, t1, s1);
		FSM<Event> fsm1 = new FSM<>("fsm1", s1, 0 /* maxHistorySize */);
		Logger origLog = Deencapsulation.getField(fsm1, "log");
		Deencapsulation.setField(fsm1, "log", log);

		fsm1.fire(e1);
		new Verifications() {
			{
				log.log(Level.FINE, withMatch(".*s1 -> s2/s2.1.*"));
				times = 1;
			}
		};

		new FullVerifications() {
			{
				unverifiedInvocations();
			}
		};

		fsm1.fire(e1);
		new Verifications() {
			{
				log.log(Level.FINE, withMatch(".*s2/s2.1 -> s1.*"));
				times = 1;
			}
		};

		Deencapsulation.setField(fsm1, "log", origLog);
	}

	// @Mocked
	// Logger log;

	@Test
	public void history(//
			@Mocked final Logger log//
	) throws Exception {
		new Expectations() {
			{
				log.isLoggable(Level.FINE);
				result = true;
			}
		};
		// create FSM:
		// s1 e1 t1 s2
		// s2 e1 t1 s1
		State<Event> s2 = new State<>("s2");
		Event e1 = new Event("e1");
		Transition<Event> t1 = new Transition<>("t1");
		State<Event> s1 = new State<>("s1", e1, t1, s2);
		FSM<Event> fsm = new FSM<>("fsm1", s1, 0 /* maxHistorySize */);
		assertEquals(fsm.getName(), "fsm1");
		Logger origLog = Deencapsulation.getField(fsm, "log");
		Deencapsulation.setField(fsm, "log", log);

		// change from s1 to s2 without history
		fsm.fire(e1);
		assertEquals(fsm.getInitialState(), s1);
		assertEquals(fsm.getCurrentState(), s2);
		assertEquals(fsm.getMaxHistorySize(), 0);
		assertEquals(fsm.getHistory().size(), 0);

		// change back to s1 with empty history
		// an history entry is added
		fsm.setMaxHistorySize(1);
		s2.addConnection(e1, t1, s1);
		fsm.fire(e1);
		assertEquals(fsm.getInitialState(), s1);
		assertEquals(fsm.getCurrentState(), s1);
		assertEquals(fsm.getMaxHistorySize(), 1);
		assertEquals(fsm.getHistory().size(), 1);
		assertEquals(fsm.getHistory().get(0).getSrcState(), s2);

		// change from s1 to s2 with a full history
		// the oldest entry of the history is removed
		fsm.fire(e1);
		assertEquals(fsm.getCurrentState(), s2);
		assertEquals(fsm.getMaxHistorySize(), 1);
		assertEquals(fsm.getHistory().size(), 1);
		assertEquals(fsm.getHistory().get(0).getSrcState(), s1);

		// increase history size and change back to s1
		// a further history entry is added
		fsm.setMaxHistorySize(2);
		fsm.fire(e1);
		assertEquals(fsm.getCurrentState(), s1);
		assertEquals(fsm.getMaxHistorySize(), 2);
		assertEquals(fsm.getHistory().size(), 2);
		assertEquals(fsm.getHistory().get(0).getSrcState(), s1);
		assertEquals(fsm.getHistory().get(1).getSrcState(), s2);

		// decrease the history size
		// the oldest entry of the history is removed
		fsm.setMaxHistorySize(1);
		assertEquals(fsm.getMaxHistorySize(), 1);
		assertEquals(fsm.getHistory().size(), 1);
		assertEquals(fsm.getHistory().get(0).getSrcState(), s2);

		// clear the history
		// the existing entry is removed
		fsm.clearHistory();
		assertEquals(fsm.getMaxHistorySize(), 1);
		assertEquals(fsm.getHistory().size(), 0);

		// try to recreate FSM with an empty history
		try {
			new FSM<>("fsm", new ArrayList<StateConnection<Event>>(), 0 /* maxHistorySize */);
			fail();
		} catch (FSMException e) {
			assertTrue(e.getMessage().contains("must not be empty"));
		}

		// recreate FSM with an existing history but max history size == 0
		// the initial state is the destination state of the history entry
		// the existing history entry is removed
		List<StateConnection<Event>> history = new ArrayList<>();
		history.add(new StateConnection<FSMTest.Event>(s1, e1, t1, s2));
		fsm = new FSM<>("fsm", history, 0 /* maxHistorySize */);
		assertEquals(fsm.getCurrentState(), s2);
		assertEquals(fsm.getMaxHistorySize(), 0);
		assertEquals(fsm.getHistory().size(), 0);
		assertEquals(history.size(), 0);

		// recreate FSM with an existing history and max history size == 1
		// the initial state is the destination state of the history entry
		history.add(new StateConnection<FSMTest.Event>(s1, e1, t1, s2));
		fsm = new FSM<>("fsm", history, 1 /* maxHistorySize */);
		assertEquals(fsm.getCurrentState(), s2);
		assertEquals(fsm.getMaxHistorySize(), 1);
		assertEquals(fsm.getHistory().size(), 1);

		Deencapsulation.setField(fsm, "log", origLog);
	}

	// @Mocked
	// Guard<Event> g1;
	// @Mocked
	// Guard<Event> g2;
	// @Mocked
	// Action<Event> exitAction;
	// @Mocked
	// Action<Event> a1;
	// @Mocked
	// Action<Event> a2;
	// @Mocked
	// Action<Event> enterAction;

	@Test
	@SuppressWarnings("unchecked")
	public void transitions(//
			@Mocked final Guard<Event> g1, @Mocked final Guard<Event> g2,
			@Mocked final Action<Event> exitAction, @Mocked final Action<Event> a1,
			@Mocked final Action<Event> a2, @Mocked final Action<Event> enterAction//
	) throws Exception {
		class Data {
			boolean g1Result = true;
			FSMGuardException g1Exception = null;
			FSMActionException a1Exception = null;
		}
		final Data data = new Data();
		new Expectations() {
			{
				g1.evaluate(withInstanceOf(State.class), withInstanceOf(Event.class),
						withInstanceOf(State.class));
				result = new Delegate<Guard<Event>>() {
					@SuppressWarnings("unused")
					boolean makeDecision(State<Event> srcState, Event event, State<Event> destState)
							throws FSMGuardException {
						if (data.g1Exception != null) {
							throw data.g1Exception;
						}
						return data.g1Result;
					}
				};
				g2.evaluate(withInstanceOf(State.class), withInstanceOf(Event.class),
						withInstanceOf(State.class));
				result = true;

				a1.perform(withInstanceOf(State.class), withInstanceOf(Event.class),
						withInstanceOf(State.class));
				result = new Delegate<Action<Event>>() {
					@SuppressWarnings("unused")
					void performAction(State<Event> srcState, Event event, State<Event> destState)
							throws FSMActionException {
						if (data.a1Exception != null) {
							throw data.a1Exception;
						}
					}
				};
			}
		};
		// create FSM:
		// s1:enterAction e1 t1:g1,g2,a1,a2 s2
		// s2:exitAction e1 t1:g1,g2,a1,a2 s1:enterAction
		final State<Event> s2 = new State<>("s2");
		s2.addExitAction(exitAction);
		final Event e1 = new Event("e1");
		Transition<Event> t1 = new Transition<>("t1", g1, a1);
		t1.addGuard(g2);
		t1.addAction(a2);
		final State<Event> s1 = new State<>("s1", e1, t1, s2);
		s1.addEntryAction(enterAction);
		s2.addConnection(e1, t1, s1);
		FSM<Event> fsm = new FSM<>("fsm1", s1, 0 /* maxHistorySize */);
		new Verifications() {
			{
				// the enter action of s1 has been executed
				enterAction.perform(null /* srcState */, null /* event */, s1);
				times = 1;
			}
		};

		// change from s1 to s2
		fsm.fire(e1);
		new Verifications() {
			{
				// the actions of the transition have been executed
				a1.perform(s1, e1, s2);
				times = 1;
				a2.perform(s1, e1, s2);
				times = 1;
			}
		};

		// use a guard to block the transition from s2 to s1
		data.g1Result = false;
		fsm.fire(e1);
		new Verifications() {
			{
				// no further action has been executed
				a1.perform(s1, e1, s2);
				times = 1;
				a2.perform(s1, e1, s2);
				times = 1;
			}
		};

		// change from s2 to s1
		data.g1Result = true;
		fsm.fire(e1);
		new Verifications() {
			{
				// the exit action of s2 has been executed
				exitAction.perform(s2, e1, s1);
				times = 1;
				// the actions of the transition have been executed
				a1.perform(s2, e1, s1);
				times = 1;
				a2.perform(s2, e1, s1);
				times = 1;
				// the enter action of s1 has been executed
				enterAction.perform(s2, e1, s1);
				times = 1;
			}
		};
	}

	// @Mocked
	// Guard<Event> g1;
	// @Mocked
	// Action<Event> a1;

	@Test
	@SuppressWarnings("unchecked")
	public void transitionsError(//
			@Mocked final Guard<Event> g1, @Mocked final Action<Event> a1//
	) throws Exception {
		class Data {
			FSMGuardException g1Exception = null;
			FSMActionException a1Exception = null;
		}
		final Data data = new Data();
		new Expectations() {
			{
				g1.evaluate(withInstanceOf(State.class), withInstanceOf(Event.class),
						withInstanceOf(State.class));
				result = new Delegate<Guard<Event>>() {
					@SuppressWarnings("unused")
					boolean evaluate(State<Event> srcState, Event event, State<Event> destState)
							throws FSMGuardException {
						if (data.g1Exception != null) {
							throw data.g1Exception;
						}
						return true;
					}
				};

				a1.perform(withInstanceOf(State.class), withInstanceOf(Event.class),
						withInstanceOf(State.class));
				result = new Delegate<Action<Event>>() {
					@SuppressWarnings("unused")
					void perform(State<Event> srcState, Event event, State<Event> destState)
							throws FSMActionException {
						if (data.a1Exception != null) {
							throw data.a1Exception;
						}
					}
				};
			}
		};
		// create FSM:
		// s1 e1 t1:g1,a1 s2
		// s2 e1 t1:g1,a1 s1
		final State<Event> s2 = new State<>("s2");
		final Event e1 = new Event("e1");
		Transition<Event> t1 = new Transition<>("t1", g1, a1);
		final State<Event> s1 = new State<>("s1", e1, new Transition<>("t1", g1, a1), s2);
		s2.addConnection(e1, t1, s1);
		FSM<Event> fsm = new FSM<>("fsm1", s1, 0 /* maxHistorySize */);

		// use a guard that throws an exception
		data.g1Exception = new FSMGuardException("huhu");
		try {
			fsm.fire(e1);
			fail();
		} catch (FSMGuardException e) {
		}

		data.g1Exception = new FSMGuardException("huhu", new Throwable("cause"));
		try {
			fsm.fire(e1);
			fail();
		} catch (FSMGuardException e) {
		}

		// use an action that throws an exception
		data.g1Exception = null;
		data.a1Exception = new FSMActionException("huhu");
		try {
			fsm.fire(e1);
			fail();
		} catch (FSMActionException e) {
		}

		data.a1Exception = new FSMActionException("huhu", new Throwable("cause"));
		try {
			fsm.fire(e1);
			fail();
		} catch (FSMActionException e) {
		}
	}
}
