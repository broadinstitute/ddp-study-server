package org.broadinstitute.dsm.model.elastic.filter;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class AndOrFilterSeparatorTest {

    @Test
    public void parseFiltersByLogicalOperators() {
        String filter = " AND c.cohort_tag_name = '7' AND c.cohort_tag_name IS NOT NULL OR c.cohort_tag_name = 'test'";
        AndOrFilterSeparator andOrFilterSeparator = new AndOrFilterSeparator(filter);
        Map<String, List<String>> map = andOrFilterSeparator.parseFiltersByLogicalOperators();
        assertEquals(2, map.get("AND").size());
        assertEquals(1, map.get("OR").size());

        List<String> andExpected = Arrays.asList("c.cohort_tag_name = '7'", "c.cohort_tag_name IS NOT NULL");

        List<String> orExpected = Arrays.asList("c.cohort_tag_name = 'test'");

        assertEquals(andExpected, map.get("AND"));
        assertEquals(orExpected, map.get("OR"));
    }

    @Test
    public void parseFiltersByLogicalOperatorsIgnore() {
        String filter = " AND c.cohort_tag_name = '7' AND c.cohort_tag_name IS NOT NULL OR c.cohort_tag_name = 'test' "
                + "AND ANGIORELEASE.createdAt = '2012-02-03' ";
        AndOrFilterSeparator andOrFilterSeparator = new AndOrFilterSeparator(filter);
        Map<String, List<String>> map = andOrFilterSeparator.parseFiltersByLogicalOperators();
        assertEquals(3, map.get("AND").size());
        assertEquals(1, map.get("OR").size());

        List<String> andExpected = Arrays.asList("c.cohort_tag_name = '7'", "c.cohort_tag_name IS NOT NULL",
                "ANGIORELEASE.createdAt = '2012-02-03'");

        List<String> orExpected = Arrays.asList("c.cohort_tag_name = 'test'");

        assertEquals(andExpected, map.get("AND"));
        assertEquals(orExpected, map.get("OR"));
    }
}
