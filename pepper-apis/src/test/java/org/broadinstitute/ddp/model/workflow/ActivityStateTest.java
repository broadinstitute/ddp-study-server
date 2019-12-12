package org.broadinstitute.ddp.model.workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ActivityStateTest {

    @Test
    public void testGetType() {
        ActivityState state = new ActivityState(1L);
        assertEquals(StateType.ACTIVITY, state.getType());
    }

    @Test
    public void testMatches() {
        ActivityState state = new ActivityState(1L);

        assertFalse(state.matches(StaticState.start()));
        assertFalse(state.matches(StaticState.done()));
        assertFalse(state.matches(new ActivityState(2L)));

        assertTrue(state.matches(new ActivityState(1L)));
    }
}
