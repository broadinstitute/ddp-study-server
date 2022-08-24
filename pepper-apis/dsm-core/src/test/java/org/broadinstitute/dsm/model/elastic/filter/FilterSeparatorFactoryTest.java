

package org.broadinstitute.dsm.model.elastic.filter;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class FilterSeparatorFactoryTest {

    @Test
    public void create() {
        assertEquals(AndOrFilterSeparator.class, FilterSeparatorFactory.create("oD", StringUtils.EMPTY).getClass());

        assertEquals(NonDsmAndOrFilterSeparator.class, FilterSeparatorFactory.create("ES", StringUtils.EMPTY).getClass());
    }
}
