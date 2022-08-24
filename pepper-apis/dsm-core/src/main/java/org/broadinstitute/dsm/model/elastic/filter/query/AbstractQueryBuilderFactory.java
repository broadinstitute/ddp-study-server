
package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterSeparatorFactory;
import org.broadinstitute.dsm.model.participant.Util;

public class AbstractQueryBuilderFactory {
    public static BaseAbstractQueryBuilder create(String alias, String filter) {
        AndOrFilterSeparator filterSeparator = FilterSeparatorFactory.create(alias, filter);
        BaseAbstractQueryBuilder abstractQueryBuilder = new BaseAbstractQueryBuilder();
        if (Util.isUnderDsmKey(alias)) {
            abstractQueryBuilder = new DsmAbstractQueryBuilder();
        }
        abstractQueryBuilder.setFilterSeparator(filterSeparator);
        abstractQueryBuilder.setFilter(filter);
        return abstractQueryBuilder;
    }

}
