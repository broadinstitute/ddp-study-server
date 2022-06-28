package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.junit.Test;

public class BooleanValueProviderTest {
    @Test
    public void testSingleBoolean() {
        BooleanValueProvider valueProvider = new BooleanValueProvider();
        Map<String, Object> esData = Map.of("isEnrolled", "true");
        Filter filter = new Filter();
        filter.setParticipantColumn(new ParticipantColumn("isEnrolled", ""));
        Collection<String> renderedValues = valueProvider.getValue("isEnrolled", esData, Alias.DATA, filter);
        assertEquals(Collections.singletonList("Yes"), renderedValues);
    }

    @Test
    public void testMultipleBooleans() {
        BooleanValueProvider valueProvider = new BooleanValueProvider();
        Map<String, Object> esData = Map.of("isEnrolled", List.of("true", "false"));
        Filter filter = new Filter();
        filter.setParticipantColumn(new ParticipantColumn("isEnrolled", ""));
        Collection<String> renderedValues = valueProvider.getValue("isEnrolled", esData, Alias.DATA, filter);
        assertEquals(List.of("Yes", "No"), renderedValues);
    }
}
