package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.statics.DBConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class TestResultCollectionQueryBuilder extends BaseQueryBuilder {

    public static final String TEST_RESULT = "testResult";

    @Override
    protected QueryBuilder build(List<QueryBuilder> queryBuilders) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        String path = String.join(DBConstants.ALIAS_DELIMITER, this.payload.getPath(), operator.getSplitterStrategy().getFieldName());
        queryBuilders.forEach(boolQueryBuilder::must);
        return new NestedQueryBuilder(path, boolQueryBuilder, ScoreMode.Avg);
    }
}
