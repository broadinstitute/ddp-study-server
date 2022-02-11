package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.junit.Assert;
import org.junit.Test;

public class DateLowerSplitterTest {

    @Test
    public void getValue() {
        BaseSplitter splitter = SplitterFactory.createSplitter(Operator.DATE_LESS_THAN_EQUALS, "");
        String filter = "AND m.mr_received  <= STR_TO_DATE('2000-01-01','%Y-%m-%d')";
        splitter.setFilter(filter);
        Assert.assertEquals("'2000-01-01'", splitter.getValue()[0]);
    }
}