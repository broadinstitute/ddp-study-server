package org.broadinstitute.dsm.model.elastic.filter.splitter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiamondEqualsSplitterTest {

    @Test
    public void getAlias() {
        DiamondEqualsSplitterStrategy diamondEqualsSplitter = getDiamondEqualsSplitter();
        assertEquals("m", diamondEqualsSplitter.getAlias());
    }

    @Test
    public void getValue() {
        DiamondEqualsSplitterStrategy diamondEqualsSplitter = getDiamondEqualsSplitter();
        assertEquals("NOT'1'", diamondEqualsSplitter.getValue()[0]);
    }

    @Test
    public void getInnerProperty() {
        DiamondEqualsSplitterStrategy diamondEqualsSplitter = getDiamondEqualsSplitter();
        assertEquals("faxSent", diamondEqualsSplitter.getInnerProperty());
    }

    private DiamondEqualsSplitterStrategy getDiamondEqualsSplitter() {
        String not = "NOT m.fax_sent <=> 1";
        DiamondEqualsSplitterStrategy diamondEqualsSplitter = new DiamondEqualsSplitterStrategy();
        diamondEqualsSplitter.setFilter(not);
        return diamondEqualsSplitter;
    }
}
