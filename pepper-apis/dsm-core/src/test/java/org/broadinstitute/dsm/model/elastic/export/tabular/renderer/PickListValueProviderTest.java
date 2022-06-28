package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;


import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.junit.Test;

public class PickListValueProviderTest {
    @Test
    public void testSinglePicklist() {
        PickListValueProvider valueProvider = new PickListValueProvider();
        Map<String, Object> esData = Map.of("married", "yes");
        Filter filter = new Filter();
        filter.setOptions(List.of(new NameValue("yes", "YES"), new NameValue("no", "YES")));
        filter.setParticipantColumn(new ParticipantColumn("married", ""));
        Collection<String> renderedValues = valueProvider.getValue("married", esData, Alias.DATA, filter);
        assertEquals(Collections.singletonList("YES"), renderedValues);
    }
}
