package org.broadinstitute.dsm.model.elastic.export.excel.renderer;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.junit.Test;

public class DateValueProviderTest {
    @Test
    public void testSingleDate() {
        LocalDateTime testDate = LocalDateTime.of(2022, 5, 17, 5, 5, 5, 5);
        DateValueProvider valueProvider = new DateValueProvider();
        Map<String, Object> esData = Map.of("dateJoined", testDate.toInstant(ZoneOffset.UTC).toEpochMilli());
        Filter filter = new Filter();
        filter.setParticipantColumn(new ParticipantColumn("dateJoined", ""));
        Collection<String> renderedValues = valueProvider.getValue("dateJoined", esData, Alias.DATA, filter);
        assertEquals(Collections.singletonList("17-05-2022 05:05:05"), renderedValues);
    }
}
