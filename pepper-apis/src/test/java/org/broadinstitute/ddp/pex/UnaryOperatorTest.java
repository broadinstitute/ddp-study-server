package org.broadinstitute.ddp.pex;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UnaryOperatorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testApply_not() {
        Object actual = UnaryOperator.NOT.apply(false);
        assertNotNull(actual);
        assertTrue(actual instanceof Boolean);
        assertTrue((Boolean) actual);

        assertFalse((Boolean) UnaryOperator.NOT.apply(true));
    }

    @Test
    public void testApply_not_nonBool() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("logical NOT"));
        UnaryOperator.NOT.apply("foo");
    }

    @Test
    public void testApply_neg() {
        Object actual = UnaryOperator.NEG.apply(25L);
        assertNotNull(actual);
        assertTrue(actual instanceof Long);
        assertEquals(-25L, actual);
    }

    @Test
    public void testApply_neg_nonNumeric() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("negation"));
        UnaryOperator.NEG.apply("foo");
    }
}
