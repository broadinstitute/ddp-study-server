package org.broadinstitute.dsm.model.elastic.export.excel.renderer;


import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ActivityStatusValueProviderTest {
    private static final Map<String, String> PREQUAL = Map.of("activityCode", "prequal", "status", "COMPLETED");
    private static final Map<String, String> CONSENT = Map.of("activityCode", "consent", "status", "COMPLETED");
    private static final Map<String, String> MEDICAL_HISTORY = Map.of("activityCode", "medical_history", "status", "IN PROGRESS");

    @Test
    public void testSingleActivity() {
        ActivityStatusValueProvider valueProvider = new ActivityStatusValueProvider();
        List<Map<String, String>> activities = Collections.singletonList(PREQUAL);
        Map<String, Object> esData = Map.of("activities", activities);
        Collection<String> renderedValues = valueProvider.getValue("activities", esData, null, null);
        assertEquals(renderedValues, Collections.singletonList("prequal : COMPLETED"));
    }

    @Test
    public void testMultipleActivities() {
        ActivityStatusValueProvider valueProvider = new ActivityStatusValueProvider();
        List<Map<String, String>> activities = List.of(PREQUAL, CONSENT, MEDICAL_HISTORY);
        Map<String, Object> esData = Map.of("activities", activities);
        Collection<String> renderedValues = valueProvider.getValue("activities", esData, null, null);
        assertEquals(Collections.singletonList("prequal : COMPLETED, consent : COMPLETED, medical_history : IN PROGRESS"), renderedValues);
    }
}
