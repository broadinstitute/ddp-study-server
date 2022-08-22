package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.statics.DBConstants;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class TestResultCollectionQueryBuilder extends BaseQueryBuilder {

    public static final String TEST_RESULT = "testResult";

    public TestResultCollectionQueryBuilder(QueryPayload queryPayload) {
        super(queryPayload);
    }

    @Override
    protected QueryBuilder getFinalQuery(QueryBuilder query) {
        String path = String.join(DBConstants.ALIAS_DELIMITER, payload.getPath(), operator.getSplitterStrategy().getFieldName());
        return new NestedQueryBuilder(path, query, ScoreMode.Avg);
    }
}
