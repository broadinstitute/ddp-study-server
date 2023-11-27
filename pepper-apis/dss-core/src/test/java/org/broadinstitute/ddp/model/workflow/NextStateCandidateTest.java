package org.broadinstitute.ddp.model.workflow;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.broadinstitute.ddp.exception.DDPException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NextStateCandidateTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testHasPrecondition() {
        NextStateCandidate candidate = new NextStateCandidate(1L, StateType.START, 2L, null);
        assertFalse(candidate.hasPrecondition());

        candidate = new NextStateCandidate(1L, StateType.START, 2L, "");
        assertFalse(candidate.hasPrecondition());

        candidate = new NextStateCandidate(1L, StateType.START, 2L, "   \t\n  ");
        assertFalse(candidate.hasPrecondition());

        candidate = new NextStateCandidate(1L, StateType.START, 2L, "true && false");
        assertTrue(candidate.hasPrecondition());
    }

    @Test
    public void testAsWorkflowstate() {
        NextStateCandidate candidate = new NextStateCandidate(1L, StateType.START, null, "true");
        WorkflowState state = candidate.asWorkflowState();
        assertTrue(state.matches(StaticState.start()));

        candidate = new NextStateCandidate(1L, StateType.DONE, null, "true");
        state = candidate.asWorkflowState();
        assertTrue(state.matches(StaticState.done()));

        candidate = new NextStateCandidate(1L, StateType.ACTIVITY, 2L, "true");
        state = candidate.asWorkflowState();
        assertTrue(state.matches(new ActivityState(2L)));
    }

    @Test
    public void testAsWorkflowState_missingActivityId() {
        thrown.expect(DDPException.class);
        thrown.expectMessage("activity id");
        NextStateCandidate candidate = new NextStateCandidate(1L, StateType.ACTIVITY, null, "true");
        candidate.asWorkflowState();
    }
}
