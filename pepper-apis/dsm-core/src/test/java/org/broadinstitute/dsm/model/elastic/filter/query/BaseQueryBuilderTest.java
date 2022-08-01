package org.broadinstitute.dsm.model.elastic.filter.query;

import static org.junit.Assert.assertTrue;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.junit.Test;

public class BaseQueryBuilderTest {


    @Test
    public void buildQueryBuilder() {
        QueryPayload payload = new QueryPayload("dsm.medicalRecord", "medicalRecordId", new Integer[] {10});
        Operator operator = Operator.EQUALS;

        CollectionQueryBuilder collectionQueryBuilder = new CollectionQueryBuilder(payload);
        collectionQueryBuilder.operator = operator;
        collectionQueryBuilder.payload = payload;

        BuildQueryStrategy queryStrategy = operator.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(collectionQueryBuilder);

        NestedQueryBuilder queryBuilder =
                (NestedQueryBuilder) collectionQueryBuilder.build(queryStrategy.build());
        assertTrue(queryBuilder.query() instanceof MatchQueryBuilder);

        operator = Operator.LIKE;
        collectionQueryBuilder.operator = operator;

        queryStrategy = operator.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(collectionQueryBuilder);

        queryBuilder = (NestedQueryBuilder) collectionQueryBuilder.build(queryStrategy.build());
        assertTrue(queryBuilder.query() instanceof MatchQueryBuilder);


        operator = Operator.GREATER_THAN_EQUALS;
        collectionQueryBuilder.operator = operator;

        queryStrategy = operator.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(collectionQueryBuilder);

        queryBuilder = (NestedQueryBuilder) collectionQueryBuilder.build(queryStrategy.build());

        assertTrue(queryBuilder.query() instanceof RangeQueryBuilder);
    }

    @Test
    public void of() {
        BaseQueryBuilder testResultCollectionQueryBuilder = BaseQueryBuilder.of(new QueryPayload.Builder()
                .withAlias("k").withProperty("testResult").build());
        BaseQueryBuilder collectionQueryBuilder = BaseQueryBuilder.of(new QueryPayload.Builder().withAlias("m").build());
        BaseQueryBuilder singleQueryBuilder = BaseQueryBuilder.of(new QueryPayload.Builder().withAlias("o").build());
        assertTrue(testResultCollectionQueryBuilder instanceof TestResultCollectionQueryBuilder);
        assertTrue(collectionQueryBuilder instanceof CollectionQueryBuilder);
        assertTrue(singleQueryBuilder instanceof SingleQueryBuilder);
    }

}
