package org.broadinstitute.dsm.model.elastic.filter.splitter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.junit.Test;

public class MultipleOptionsSplitterTest {

    @Test
    public void getInnerProperty() {
        String filter = "( oD.fax_sent = 'review' OR oD.fax_sent = 'no' OR oD.fax_sent = 'hold' OR oD.fax_sent = 'request' OR oD.fax_sent = " +
                "'unable To Obtain' OR oD.fax_sent = 'sent' OR oD.fax_sent = 'received' OR oD.fax_sent = 'returned' )";
        BaseSplitter multipleSplitter = SplitterFactory.createSplitter(Operator.MULTIPLE_OPTIONS, "");
        multipleSplitter.setFilter(filter);
        assertEquals("faxSent", multipleSplitter.getInnerProperty());
    }

    @Test
    public void contentContainsOR() {
        String filter = "( d.status = 'EXITED_BEFORE_ENROLLMENT' OR d.status = 'EXITED_AFTER_ENROLLMENT' )";
        String[] filters = new String[] {"d.status = 'EXITED_BEFORE_ENROLLMENT'", "d.status = 'EXITED_AFTER_ENROLLMENT'"};
        BaseSplitter multipleSplitter = SplitterFactory.createSplitter(Operator.MULTIPLE_OPTIONS, "");
        multipleSplitter.setFilter(filter);
        String[] actualFilters = multipleSplitter.split();
        assertArrayEquals(filters, actualFilters);
    }
}