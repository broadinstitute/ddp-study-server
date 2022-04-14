package org.broadinstitute.ddp.export.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ActivityMetadataCollectorTest {

    @Test
    public void testEmptyRow() {
        var collector = new ActivityMetadataCollector();

        List<String> actual = collector.emptyRow(false);
        assertEquals(5, actual.size());

        actual = collector.emptyRow(true);
        assertEquals(6, actual.size());
        assertEquals("", actual.get(0));
    }

    @Test
    public void testMappings() {
        var collector = new ActivityMetadataCollector();
        Map<String, Object> actual = collector.mappings("foo_v1", true);
        assertTrue(actual.containsKey("foo_v1_parent"));
    }

    @Test
    public void testHeaders() {
        var collector = new ActivityMetadataCollector();

        List<String> actual = collector.headers("foo_v1", true);
        assertTrue(actual.contains("foo_v1_parent"));

        actual = collector.headers("foo_v1", true, 5);
        assertTrue(actual.contains("foo_v1_5_parent"));
    }
}
