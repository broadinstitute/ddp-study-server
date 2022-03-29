package org.broadinstitute.dsm.model.elastic.filter.splitter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JsonContainsSplitterTest {

    @Test
    public void getValue() {
        //JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))
        SplitterStrategy splitter = new JsonContainsSplitterStrategy();
        String filter = "JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))";
        splitter.setFilter(filter);
        assertEquals("'true'", splitter.getValue()[0]);
    }

    @Test
    public void getInnerProperty() {
        //JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))
        SplitterStrategy splitter = new JsonContainsSplitterStrategy();
        String filter = "JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))";
        splitter.setFilter(filter);
        assertEquals("testResult.isCorrected", splitter.getInnerProperty());
    }

    @Test
    public void getFieldWithAlias() {
        //JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))
        // [k, test_result]
        SplitterStrategy splitter = new JsonContainsSplitterStrategy();
        String filter = "JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))";
        splitter.setFilter(filter);
        assertEquals("k", splitter.getFieldWithAlias()[0]);
        assertEquals("test_result", splitter.getFieldWithAlias()[1]);
    }

    @Test
    public void getField() {
        SplitterStrategy splitter = new JsonContainsSplitterStrategy();
        String filter = "JSON_CONTAINS(k.test_result, JSON_OBJECT('isCorrected', 'true'))";
        splitter.setFilter(filter);
        assertEquals("testResult", splitter.getFieldName());
    }


}
