package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.junit.Assert;
import org.junit.Test;

public class DateSplitterTest {

    @Test
    public void split() {

        String filter = "DATE(FROM_UNIXTIME(k.scan_date/1000))  = DATE(FROM_UNIXTIME(1640563200))";
        SplitterStrategy splitter = Operator.DATE.getStrategy();
        splitter.setFilter(filter);

        Assert.assertEquals("1640563200", splitter.getValue()[0]);
        Assert.assertEquals("scanDate", splitter.getInnerProperty());

    }
}
