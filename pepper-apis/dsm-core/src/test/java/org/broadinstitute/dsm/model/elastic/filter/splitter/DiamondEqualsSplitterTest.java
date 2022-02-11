package org.broadinstitute.dsm.model.elastic.filter.splitter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiamondEqualsSplitterTest {

    @Test
    public void getAlias() {
        DiamondEqualsSplitter diamondEqualsSplitter = getDiamondEqualsSplitter();
        assertEquals("m", diamondEqualsSplitter.getAlias());
    }

    @Test
    public void getValue() {
        DiamondEqualsSplitter diamondEqualsSplitter = getDiamondEqualsSplitter();
        assertEquals("NOT'1'", diamondEqualsSplitter.getValue()[0]);
    }

    @Test
    public void getInnerProperty() {
        DiamondEqualsSplitter diamondEqualsSplitter = getDiamondEqualsSplitter();
        assertEquals("faxSent", diamondEqualsSplitter.getInnerProperty());
    }

    private DiamondEqualsSplitter getDiamondEqualsSplitter() {
        String not = "NOT m.fax_sent <=> 1";
        DiamondEqualsSplitter diamondEqualsSplitter = new DiamondEqualsSplitter();
        diamondEqualsSplitter.setFilter(not);
        return diamondEqualsSplitter;
    }
}