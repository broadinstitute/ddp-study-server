package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;

public class AbstractQueryBuilderFactory {
    public static BaseAbstractQueryBuilder create(String filter) {
        AndOrFilterSeparator filterSeparator = new AndOrFilterSeparator(filter);
        BaseAbstractQueryBuilder abstractQueryBuilder = new BaseAbstractQueryBuilder();
        abstractQueryBuilder.setFilterSeparator(filterSeparator);
        abstractQueryBuilder.setFilter(filter);
        return abstractQueryBuilder;
    }

}
