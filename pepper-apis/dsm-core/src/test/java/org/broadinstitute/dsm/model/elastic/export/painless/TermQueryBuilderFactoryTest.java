package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.Arrays;

import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.junit.Assert;
import org.junit.Test;

public class TermQueryBuilderFactoryTest {

    @Test
    public void instance() {

        Object value = "TEST01";
        String fieldName = "profile.guid";
        TermQueryBuilderFactory termQueryBuilderFactory = new TermQueryBuilderFactory(fieldName, value);
        Assert.assertTrue(termQueryBuilderFactory.instance() instanceof TermQueryBuilder);

        Object values = Arrays.asList("TEST01", "TEST02");
        termQueryBuilderFactory = new TermQueryBuilderFactory(fieldName, values);
        Assert.assertTrue(termQueryBuilderFactory.instance() instanceof TermsQueryBuilder);
    }


}
