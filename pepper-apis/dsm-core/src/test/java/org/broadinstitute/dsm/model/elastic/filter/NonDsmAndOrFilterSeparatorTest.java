package org.broadinstitute.dsm.model.elastic.filter;


import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NonDsmAndOrFilterSeparatorTest {

    @Test
    public void checkRegex() {
        String regex = "([A-z]|\\(|\\s)+(\\.|\\s)*(\\.)*";
        String orRegex = "(OR) " + regex;
        String andRegex = "(AND) " + regex;
        AndOrFilterSeparator nonDsmAndOrFilterSeparatorTest = new NonDsmAndOrFilterSeparator("");
        assertEquals(regex, nonDsmAndOrFilterSeparatorTest.getRegex());
        assertEquals(orRegex, nonDsmAndOrFilterSeparatorTest.orDsmAliasRegex);
        assertEquals(andRegex, nonDsmAndOrFilterSeparatorTest.andDsmAliasRegex);
    }


    @Test
    public void parseFiltersByLogicalOperators() {
        String filter = " AND ANGIORELEASE.createdAt = '2012-02-03' "
                + "OR ANGIORELEASE.createdAt = '2021-03-03' "
                + "AND ANGIORELEASE.updatedAt = '2023-03-04'";
        AndOrFilterSeparator andOrFilterSeparator = new NonDsmAndOrFilterSeparator(filter);
        Map<String, List<String>> map =
                andOrFilterSeparator.parseFiltersByLogicalOperators();
        assertEquals(2, map.get("AND").size());
        assertEquals(1, map.get("OR").size());

        List<String> andExpected = Arrays.asList(
                "ANGIORELEASE.createdAt = '2012-02-03'",
                "ANGIORELEASE.updatedAt = '2023-03-04'"
        );

        List<String> orExpected = Arrays.asList(
                "ANGIORELEASE.createdAt = '2021-03-03'"
        );

        assertEquals(andExpected, map.get("AND"));
        assertEquals(orExpected, map.get("OR"));
    }

}
