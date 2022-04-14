package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.splitter.GreaterThanEqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.IsNullSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.JsonExtractSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.LessThanEqualsSplitterStrategy;
import org.elasticsearch.index.query.QueryBuilder;

public class JsonExtractQueryStrategy implements BuildQueryStrategy {
    @Override
    public QueryBuilder build(BaseQueryBuilder baseQueryBuilder) {
        QueryBuilder qb;
        Object[] dynamicFieldValues = baseQueryBuilder.payload.getValues();
        JsonExtractSplitterStrategy jsonExtractSplitter = (JsonExtractSplitterStrategy) baseQueryBuilder.operator.getSplitterStrategy();
        if (!StringUtils.EMPTY.equals(dynamicFieldValues[0])) {
            if (jsonExtractSplitter.getDecoratedSplitter() instanceof GreaterThanEqualsSplitterStrategy) {
                qb = new RangeGTEQueryStrategy().build(baseQueryBuilder);
            } else if (jsonExtractSplitter.getDecoratedSplitter() instanceof LessThanEqualsSplitterStrategy) {
                qb = new RangeLTEQueryStrategy().build(baseQueryBuilder);
            } else {
                qb = new MatchQueryStrategy().build(baseQueryBuilder);
            }
            baseQueryBuilder.build(qb);
        } else {
            if (jsonExtractSplitter.getDecoratedSplitter() instanceof IsNullSplitterStrategy) {
                qb = new MustNotExistsQueryStrategy().build(baseQueryBuilder);
            } else {
                qb = new MustExistsQueryStrategy().build(baseQueryBuilder);
            }
        }
        return qb;
    }
}
