package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.junit.Assert;
import org.junit.Test;

public class LikeSplitterTest {

    @Test
    public void split() {
        String filter = "JSON_EXTRACT ( d.additional_values_json , '$.status' )   LIKE  '%EXITED_BEFORE_ENROLLMENT%'";
        BaseSplitter splitter = SplitterFactory.createSplitter(Operator.JSON_EXTRACT, filter);
        splitter.setFilter(filter);
        String actual = splitter.getValue()[0];
        Assert.assertEquals("'EXITED_BEFORE_ENROLLMENT'", actual);
    }
}