package org.broadinstitute.dsm.model.elastic.filter.query;

import static org.junit.Assert.*;

import org.junit.Test;

public class AbstractQueryBuilderFactoryTest {

    @Test
    public void create() {
        AbstractQueryBuilderFactory queryBuilderFactory = new AbstractQueryBuilderFactory("oD");
        assertEquals(DsmAbstractQueryBuilder.class,  queryBuilderFactory.create().getClass());

        queryBuilderFactory = new AbstractQueryBuilderFactory("ES");
        assertEquals(BaseAbstractQueryBuilder.class,  queryBuilderFactory.create().getClass());
    }
}