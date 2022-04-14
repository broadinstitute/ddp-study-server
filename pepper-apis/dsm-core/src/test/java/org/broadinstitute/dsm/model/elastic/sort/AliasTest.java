package org.broadinstitute.dsm.model.elastic.sort;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AliasTest {

    @Test
    public void valueOf() {
        SortBy.Builder sortByBuilder = new SortBy.Builder()
                .withTableAlias("m");
        assertEquals(Alias.M, Alias.of(sortByBuilder.build()));
        assertEquals(Alias.ACTIVITIES, Alias.of(sortByBuilder.withTableAlias("unknown").build()));
    }

}
