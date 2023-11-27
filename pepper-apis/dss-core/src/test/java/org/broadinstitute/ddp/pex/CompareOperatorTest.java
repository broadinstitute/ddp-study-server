package org.broadinstitute.ddp.pex;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CompareOperatorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testApply_less() {
        var op = CompareOperator.LESS;
        assertTrue(op.apply(1L, 2L));
        assertFalse(op.apply(2L, 0L));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2013, 3, 14)));
        assertFalse(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(1989, 3, 14)));
    }

    @Test
    public void testApply_lessEq() {
        var op = CompareOperator.LESS_EQ;
        assertTrue(op.apply(1L, 2L));
        assertTrue(op.apply(1L, 1L));
        assertFalse(op.apply(2L, 0L));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2013, 3, 14)));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2012, 3, 14)));
        assertFalse(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(1989, 3, 14)));
    }

    @Test
    public void testApply_greater() {
        var op = CompareOperator.GREATER;
        assertTrue(op.apply(2L, 1L));
        assertFalse(op.apply(0L, 2L));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(1989, 3, 14)));
        assertFalse(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2013, 3, 14)));
    }

    @Test
    public void testApply_greaterEq() {
        var op = CompareOperator.GREATER_EQ;
        assertTrue(op.apply(2L, 1L));
        assertTrue(op.apply(2L, 2L));
        assertFalse(op.apply(0L, 2L));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(1989, 3, 14)));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2012, 3, 14)));
        assertFalse(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2013, 3, 14)));
    }

    @Test
    public void testApply_eq() {
        var op = CompareOperator.EQ;
        assertTrue(op.apply(false, false));
        assertFalse(op.apply(true, false));
        assertTrue(op.apply(1L, 1L));
        assertFalse(op.apply(0L, 2L));
        assertTrue(op.apply("a", "a"));
        assertFalse(op.apply("a", " a "));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2012, 3, 14)));
        assertFalse(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2012, 6, 14)));
    }

    @Test
    public void testApply_notEq() {
        var op = CompareOperator.NOT_EQ;
        assertFalse(op.apply(false, false));
        assertTrue(op.apply(true, false));
        assertFalse(op.apply(1L, 1L));
        assertTrue(op.apply(0L, 2L));
        assertFalse(op.apply("a", "a"));
        assertTrue(op.apply("a", " a "));
        assertFalse(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2012, 3, 14)));
        assertTrue(op.apply(LocalDate.of(2012, 3, 14), LocalDate.of(2012, 6, 14)));
    }

    @Test
    public void testApply_wrongTypes() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("int and date"));
        CompareOperator.LESS.apply("a", "b");
    }

    @Test
    public void testApply_intMismatch() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("int value"));
        CompareOperator.LESS.apply(1L, "b");
    }

    @Test
    public void testApply_dateMismatch() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("date value"));
        CompareOperator.LESS.apply(LocalDate.of(2012, 3, 14), "b");
    }
}
