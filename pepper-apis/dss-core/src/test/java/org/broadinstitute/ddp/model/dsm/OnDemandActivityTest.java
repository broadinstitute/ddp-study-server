package org.broadinstitute.ddp.model.dsm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OnDemandActivityTest {

    @Test
    public void test_NullMaxInstances_isRepeating() {
        OnDemandActivity activity = new OnDemandActivity("abc", null);
        assertEquals(OnDemandActivity.RepeatType.REPEATING, activity.getType());
    }

    @Test
    public void test_ZeroMaxInstances_isNonRepeating() {
        OnDemandActivity activity = new OnDemandActivity("abc", 0L);
        assertEquals(OnDemandActivity.RepeatType.NONREPEATING, activity.getType());
    }

    @Test
    public void test_OneMaxInstances_isNonRepeating() {
        OnDemandActivity activity = new OnDemandActivity("abc", 1L);
        assertEquals(OnDemandActivity.RepeatType.NONREPEATING, activity.getType());
    }

    @Test
    public void test_ManyMaxInstances_isRepeating() {
        OnDemandActivity activity = new OnDemandActivity("abc", 2L);
        assertEquals(OnDemandActivity.RepeatType.REPEATING, activity.getType());

        activity = new OnDemandActivity("abc", 3L);
        assertEquals(OnDemandActivity.RepeatType.REPEATING, activity.getType());

        activity = new OnDemandActivity("abc", 10L);
        assertEquals(OnDemandActivity.RepeatType.REPEATING, activity.getType());

        activity = new OnDemandActivity("abc", 100L);
        assertEquals(OnDemandActivity.RepeatType.REPEATING, activity.getType());

        activity = new OnDemandActivity("abc", 10_000L);
        assertEquals(OnDemandActivity.RepeatType.REPEATING, activity.getType());
    }
}
