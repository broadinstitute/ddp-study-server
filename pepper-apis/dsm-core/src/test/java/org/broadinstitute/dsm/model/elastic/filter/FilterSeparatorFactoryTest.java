package org.broadinstitute.dsm.model.elastic.filter;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class FilterSeparatorFactoryTest {

    @Test
    public void create() {
        FilterSeparatorFactory separatorFactory = new FilterSeparatorFactory("oD", StringUtils.EMPTY);
        assertEquals(AndOrFilterSeparator.class, separatorFactory.create().getClass());

        separatorFactory = new FilterSeparatorFactory("ES", StringUtils.EMPTY);
        assertEquals(NonDsmAndOrFilterSeparator.class, separatorFactory.create().getClass());
    }
}