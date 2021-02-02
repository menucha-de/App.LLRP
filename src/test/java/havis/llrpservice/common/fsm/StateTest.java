package havis.llrpservice.common.fsm;

import org.testng.Assert;
import org.testng.annotations.Test;

public class StateTest {

	enum Event {
		A, B
	}

	@Test
	public void state() {
		Assert.assertEquals(new State<Event>("s1").getName(), "s1");

		State<Event> s1 = new State<>("s1", Event.A, new Transition<Event>("t1"),
				new State<Event>("s2"));
		Assert.assertEquals(s1.getName(), "s1");
		Assert.assertEquals(s1.getConnections().size(), 1);

		s1 = new State<>("s1", new State<Event>("s2"));
		Assert.assertEquals(s1.getName(), "s1");
		Assert.assertEquals(s1.getParent().getName(), "s2");

		s1 = new State<>("s1", new State<Event>("s2"), Event.A, new Transition<Event>("t1"),
				new State<Event>("s2"));
		Assert.assertEquals(s1.getName(), "s1");
		Assert.assertEquals(s1.getParent().getName(), "s2");
		Assert.assertEquals(s1.getConnections().size(), 1);
		Assert.assertEquals(s1.getConnections().get(0).getEvent(), Event.A);

		s1.addConnection(Event.B, new Transition<Event>("t2"), new State<Event>("s3"));
		Assert.assertEquals(s1.getConnections().size(), 2);
		Assert.assertEquals(s1.getConnections().get(0).getEvent(), Event.A);
		Assert.assertEquals(s1.getConnections().get(1).getEvent(), Event.B);

		Action<Event> action = new Action<Event>() {

			@Override
			public void perform(State<Event> srcState, Event event, State<Event> destState)
					throws FSMActionException {
			}
		};
		s1.addEntryAction(action);
		Assert.assertEquals(s1.getEntryActions().get(0), action);

		action = new Action<Event>() {

			@Override
			public void perform(State<Event> srcState, Event event, State<Event> destState)
					throws FSMActionException {
			}
		};
		s1.addExitAction(action);
		Assert.assertEquals(s1.getExitActions().get(0), action);
	}
}
