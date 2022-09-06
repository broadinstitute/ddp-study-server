package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.NonDsmAndOrFilterSeparator;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.junit.Assert;
import org.junit.Test;

public class SingleQueryBuilderTest {

    @Test
    public void buildEachQuery() {

        String filter = "AND p.participantId = '1234'";

        BaseAbstractQueryBuilder abstractQueryBuilder = AbstractQueryBuilderFactory.create("p", filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder().must(
                new MatchQueryBuilder("dsm.participant.participantId", "1234").operator(Operator.AND)
        );

        Assert.assertEquals(expected, actual);
    }


    @Test
    public void dateNotEmpty() {

        String andFilter = " AND dsm.dateOfBirth IS NOT NULL ";

        BoolQueryBuilder mustExists = new BoolQueryBuilder();
        mustExists.must(new ExistsQueryBuilder("dsm.dateOfBirth"));
        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(mustExists);

        Assert.assertEquals(expectedQuery, getNonActivityQueryBuilder(andFilter));

        String orFilter = " OR dsm.dateOfBirth IS NOT NULL ";

        BoolQueryBuilder mustExists2 = new BoolQueryBuilder();
        mustExists2.must(new ExistsQueryBuilder("dsm.dateOfBirth"));
        BoolQueryBuilder expectedQuery2 = new BoolQueryBuilder();
        expectedQuery2.should(mustExists2);

        Assert.assertEquals(expectedQuery2, getNonActivityQueryBuilder(orFilter));

    }

    @Test
    public void dateWithValue() {
        String andFilter = " AND dsm.dateOfBirth  = '1990-11-25'";

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(new MatchQueryBuilder("dsm.dateOfBirth", "1990-11-25").operator(Operator.AND));

        Assert.assertEquals(expectedQuery, getNonActivityQueryBuilder(andFilter));
    }

    @Test
    public void twoValueNotEmpty() {
        String filter = " AND profile.firstName IS NOT NULL  AND profile.lastName IS NOT NULL ";

        BoolQueryBuilder mustExists1 = new BoolQueryBuilder();
        mustExists1.must(new ExistsQueryBuilder("profile.firstName"));

        BoolQueryBuilder mustExists2 = new BoolQueryBuilder();
        mustExists2.must(new ExistsQueryBuilder("profile.lastName"));

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(mustExists1);
        expectedQuery.must(mustExists2);

        Assert.assertEquals(expectedQuery, getNonActivityQueryBuilder(filter));
    }

    @Test
    public void integerValue() {
        String filter = " AND dsm.diagnosisYear = 2014";

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(new MatchQueryBuilder("dsm.diagnosisYear", 2014L).operator(Operator.AND));

        Assert.assertEquals(expectedQuery, getNonActivityQueryBuilder(filter));

    }

    @Test
    public void twoDifferentValue() {
        String filter = " AND profile.doNotContact = true AND dsm.diagnosisYear = 2014 OR dsm.diagnosisYear = 2015";

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        expectedQuery.must(new MatchQueryBuilder("profile.doNotContact", true).operator(Operator.AND));
        expectedQuery.must(new MatchQueryBuilder("dsm.diagnosisYear", 2014L).operator(Operator.AND));
        expectedQuery.should(new MatchQueryBuilder("dsm.diagnosisYear", 2015L).operator(Operator.AND));

        Assert.assertEquals(expectedQuery, getNonActivityQueryBuilder(filter));

    }

    private AbstractQueryBuilder<?> getNonActivityQueryBuilder(String filter) {
        BaseAbstractQueryBuilder abstractQueryBuilder = new BaseAbstractQueryBuilder();
        abstractQueryBuilder.setFilter(filter);
        abstractQueryBuilder.setFilterSeparator(new NonDsmAndOrFilterSeparator(filter));
        AbstractQueryBuilder<?> actualQuery = abstractQueryBuilder.build();
        return actualQuery;
    }

}
