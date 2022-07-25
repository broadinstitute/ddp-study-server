package org.broadinstitute.dsm.model.elastic.filter;

import static org.junit.Assert.*;

import org.junit.Test;

public class NonDsmAndOrFilterSeparatorTest {

    @Test
    public void checkRegex() {
        String regex = "[A-z]+(\\.|\\s)*(\\.)*";
        AndOrFilterSeparator nonDsmAndOrFilterSeparatorTest = new NonDsmAndOrFilterSeparator("");
        assertEquals(regex, nonDsmAndOrFilterSeparatorTest.DSM_ALIAS_REGEX);
    }


}