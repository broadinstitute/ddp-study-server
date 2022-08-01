package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class SingleQueryBuilderTest {

    @Test
    public void buildEachQuery() {

        String filter = "AND p.participantId = '1234'";

        BaseAbstractQueryBuilder abstractQueryBuilder = AbstractQueryBuilderFactory.create("p", filter);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> actual = abstractQueryBuilder.build();
        BoolQueryBuilder expected = new BoolQueryBuilder().must(new MatchQueryBuilder("dsm.participant.participantId", "1234"));

        Assert.assertEquals(expected, actual);
    }

}
