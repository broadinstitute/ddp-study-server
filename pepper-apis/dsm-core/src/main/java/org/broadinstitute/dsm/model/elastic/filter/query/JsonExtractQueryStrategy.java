package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.splitter.GreaterThanEqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.IsNullSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.JsonExtractSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.LessThanEqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.LikeSplitterStrategy;
import org.elasticsearch.index.query.QueryBuilder;

public class JsonExtractQueryStrategy extends BaseQueryStrategy {

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        QueryBuilder qb;
        Object[] dynamicFieldValues = baseQueryBuilder.payload.getValues();
        JsonExtractSplitterStrategy jsonExtractSplitter = (JsonExtractSplitterStrategy) baseQueryBuilder.operator.getSplitterStrategy();
        if (!StringUtils.EMPTY.equals(dynamicFieldValues[0])) {
            if (jsonExtractSplitter.getDecoratedSplitter() instanceof GreaterThanEqualsSplitterStrategy) {
                qb = new RangeGTEQueryStrategy().getMainQueryBuilder(baseQueryBuilder);
            } else if (jsonExtractSplitter.getDecoratedSplitter() instanceof LessThanEqualsSplitterStrategy) {
                qb = new RangeLTEQueryStrategy().getMainQueryBuilder(baseQueryBuilder);
            } else if (jsonExtractSplitter.getDecoratedSplitter() instanceof LikeSplitterStrategy) {
                qb = new NonExactMatchQueryStrategy().getMainQueryBuilder(baseQueryBuilder);
            } else {
                qb = new MatchQueryStrategy().getMainQueryBuilder(baseQueryBuilder);
            }
        } else {
            if (jsonExtractSplitter.getDecoratedSplitter() instanceof IsNullSplitterStrategy) {
                qb = new MustNotExistsQueryStrategy().getMainQueryBuilder(baseQueryBuilder);
            } else {
                qb = new MustExistsQueryStrategy().getMainQueryBuilder(baseQueryBuilder);
            }
        }
        return qb;
    }
}
