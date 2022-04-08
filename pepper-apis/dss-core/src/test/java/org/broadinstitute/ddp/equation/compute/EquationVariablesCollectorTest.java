package org.broadinstitute.ddp.equation.compute;

import com.google.common.collect.Sets;
import org.broadinstitute.ddp.equation.EquationVariablesCollector;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class EquationVariablesCollectorTest {
    @Test
    public void testNoVariables() {
        assertEquals(new HashSet<>(), collect("3.14"));
    }

    @Test
    public void testWithVariables() {
        assertEquals(Sets.newHashSet("x", "y", "z"), collect("x + y + z + 10"));
    }

    private static void assertEquals(final Set<String> expected, final Set<String> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        Assert.assertEquals(expected, actual);
    }

    private Set<String> collect(final String expression) {
        return new EquationVariablesCollector(expression).collect();
    }
}
