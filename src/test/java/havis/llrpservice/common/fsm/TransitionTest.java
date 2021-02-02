package havis.llrpservice.common.fsm;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TransitionTest {

	enum Event {
		A
	}

	@Test
	public void Transition() {
		// constructor with name
		Assert.assertEquals(new Transition<Event>("t1").getName(), "t1");

		// constructor with name + guard
		Transition<Event> t1 = new Transition<Event>("t1", new Guard<Event>() {

			@Override
			public boolean evaluate(State<Event> srcState, Event event, State<Event> destState)
					throws FSMGuardException {
				return true;
			}
		});
		Assert.assertEquals(t1.getGuards().size(), 1);

		// constructor with name + action
		t1 = new Transition<Event>("t1", new Action<Event>() {

			@Override
			public void perform(State<Event> srcState, Event event, State<Event> destState)
					throws FSMActionException {
			}
		});
		Assert.assertEquals(t1.getActions().size(), 1);

		// constructor with name + guard + action
		t1 = new Transition<Event>("t1", new Guard<Event>() {

			@Override
			public boolean evaluate(State<Event> srcState, Event event, State<Event> destState)
					throws FSMGuardException {
				return true;
			}
		}, new Action<Event>() {

			@Override
			public void perform(State<Event> srcState, Event event, State<Event> destState)
					throws FSMActionException {
			}
		});
		Assert.assertEquals(t1.getGuards().size(), 1);
		Assert.assertEquals(t1.getActions().size(), 1);

		// addGuard
		t1.addGuard(new Guard<TransitionTest.Event>() {

			@Override
			public boolean evaluate(State<Event> srcState, Event event, State<Event> destState)
					throws FSMGuardException {
				return false;
			}
		});
		Assert.assertEquals(t1.getGuards().size(), 2);

		// addAction
		t1.addAction(new Action<TransitionTest.Event>() {

			@Override
			public void perform(State<Event> srcState, Event event, State<Event> destState)
					throws FSMActionException {
			}
		});
		Assert.assertEquals(t1.getActions().size(), 2);
	}
}
