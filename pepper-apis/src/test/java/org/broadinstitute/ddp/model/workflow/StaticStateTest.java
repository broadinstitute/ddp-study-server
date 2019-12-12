package org.broadinstitute.ddp.model.workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StaticStateTest {

    @Test
    public void testGetType() {
        assertEquals(StateType.START, StaticState.start().getType());
        assertEquals(StateType.DONE, StaticState.done().getType());
    }

    @Test
    public void testMatches() {
        StaticState start = StaticState.start();
        StaticState done = StaticState.done();

        assertFalse(start.matches(done));
        assertFalse(start.matches(new ActivityState(1L)));
        assertTrue(start.matches(StaticState.start()));

        assertFalse(done.matches(start));
        assertFalse(done.matches(new ActivityState(1L)));
        assertTrue(done.matches(StaticState.done()));
    }
}
