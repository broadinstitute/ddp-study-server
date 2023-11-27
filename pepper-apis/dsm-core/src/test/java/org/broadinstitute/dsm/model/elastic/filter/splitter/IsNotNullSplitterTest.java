package org.broadinstitute.dsm.model.elastic.filter.splitter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IsNotNullSplitterTest {

    @Test
    public void getNestedInnerProperty() {
        SplitterStrategy splitter = new IsNotNullSplitterStrategy();
        String filter = "k.test_result.isCompleted IS NOT NULL";
        splitter.setFilter(filter);
        assertEquals("testResult.isCompleted", splitter.getInnerProperty());
    }

    @Test
    public void getInnerProperty() {
        SplitterStrategy splitter = new IsNotNullSplitterStrategy();
        String filter = "k.kit_label IS NOT NULL";
        splitter.setFilter(filter);
        assertEquals("kitLabel", splitter.getInnerProperty());
    }

    @Test
    public void getInnerPropertyAdditionalValuesJson() {
        SplitterStrategy splitter = new IsNotNullSplitterStrategy();
        String filter = "k.additional_tissue_value_json IS NOT NULL";
        splitter.setFilter(filter);
        assertEquals("dynamicFields", splitter.getInnerProperty());
    }
}
