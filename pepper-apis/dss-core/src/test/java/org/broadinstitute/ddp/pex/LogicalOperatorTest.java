package org.broadinstitute.ddp.pex;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.broadinstitute.ddp.pex.lang.PexParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class LogicalOperatorTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private TreeWalkInterpreter.PexValueVisitor mockVisitor;
    private PexParser.ExprContext mockLeftExpr;
    private PexParser.ExprContext mockRightExpr;

    @Before
    public void setup() {
        mockVisitor = mock(TreeWalkInterpreter.PexValueVisitor.class);
        mockLeftExpr = mock(PexParser.ExprContext.class);
        mockRightExpr = mock(PexParser.ExprContext.class);
    }

    @Test
    public void testApply_and() {
        var op = LogicalOperator.AND;

        when(mockLeftExpr.accept(any())).thenReturn(true);
        when(mockRightExpr.accept(any())).thenReturn(true);
        assertTrue(op.apply(mockVisitor, mockLeftExpr, mockRightExpr));

        when(mockLeftExpr.accept(any())).thenReturn(false);
        when(mockRightExpr.accept(any())).thenReturn("not-bool");
        assertFalse("should have short-circuited", op.apply(mockVisitor, mockLeftExpr, mockRightExpr));
    }

    @Test
    public void testApply_and_nonBool() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("only supports bool values"));

        var op = LogicalOperator.AND;
        when(mockLeftExpr.accept(any())).thenReturn(true);
        when(mockRightExpr.accept(any())).thenReturn("not-bool");
        op.apply(mockVisitor, mockLeftExpr, mockRightExpr);
    }

    @Test
    public void testApply_or() {
        var op = LogicalOperator.OR;

        when(mockLeftExpr.accept(any())).thenReturn(false);
        when(mockRightExpr.accept(any())).thenReturn(true);
        assertTrue(op.apply(mockVisitor, mockLeftExpr, mockRightExpr));

        when(mockLeftExpr.accept(any())).thenReturn(true);
        when(mockRightExpr.accept(any())).thenReturn("not-bool");
        assertTrue("should have short-circuited", op.apply(mockVisitor, mockLeftExpr, mockRightExpr));
    }

    @Test
    public void testApply_or_nonBool() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("only supports bool values"));

        var op = LogicalOperator.OR;
        when(mockLeftExpr.accept(any())).thenReturn(false);
        when(mockRightExpr.accept(any())).thenReturn("not-bool");
        op.apply(mockVisitor, mockLeftExpr, mockRightExpr);
    }
}
