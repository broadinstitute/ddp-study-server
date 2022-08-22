package org.broadinstitute.dsm.model.elastic.filter.query;

import static org.junit.Assert.assertEquals;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class AbstractQueryBuilderFactoryTest {

    @Test
    public void create() {
        assertEquals(DsmAbstractQueryBuilder.class,  AbstractQueryBuilderFactory.create("oD", StringUtils.EMPTY).getClass());

        assertEquals(BaseAbstractQueryBuilder.class,  AbstractQueryBuilderFactory.create("ES", StringUtils.EMPTY).getClass());
    }
}
