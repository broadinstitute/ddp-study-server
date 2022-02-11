package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JsonExtractSplitterTest {

    String filter;
    BaseSplitter splitter;

    @Before
    public void setUp() {
        filter = "JSON_EXTRACT ( m.additional_values_json , '$.seeingIfBugExists' ) = 'true'";
        splitter = SplitterFactory.createSplitter(Operator.JSON_EXTRACT, filter);
        splitter.setFilter(filter);
    }

    @Test
    public void getValue() {
        Assert.assertEquals("'true'", splitter.getValue()[0]);
    }

    @Test
    public void getInnerProperty() {
        Assert.assertEquals("dynamicFields.seeingIfBugExists", splitter.getInnerProperty());
    }

    @Test
    public void getInnerPropertyIfUpperCaseAfterDot() {
        splitter.setFilter("JSON_EXTRACT ( m.additional_values_json , '$.Scooby' ) = 'true'");
        Assert.assertEquals("dynamicFields.scooby", splitter.getInnerProperty());
    }

    @Test
    public void getIsNotNullValue() {
        filter = "JSON_EXTRACT ( m.additional_values_json , '$.seeingIfBugExists' ) IS NOT NULL";
        splitter.setFilter(filter);
        Assert.assertEquals(StringUtils.EMPTY, splitter.getValue()[0]);
    }
}