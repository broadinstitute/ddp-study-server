
package org.broadinstitute.dsm.model.filter.prefilter;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class BasicPreFilterQueryProcessorTest {

    @Test
    public void merge() {
        var originalFilters = new HashMap<>(Map.of(
                "m", " AND m.medical_record_id = 15",
                "oD", " AND oD.facility LIKE 'something'",
                "c", " AND c.ddp_instance_id = 12"
        ));
        var queryMerger     = new BasicPreFilterQueryProcessor(originalFilters);
        var newFilter = queryMerger.update(" AND c.cohort_tag_name = 'OS PE-CGS'");
        var expectedQuery = " AND c.ddp_instance_id = 12 AND c.cohort_tag_name = 'OS PE-CGS'";
        var actual = newFilter.get("c");
        Assert.assertEquals(expectedQuery, actual);
    }

    @Test
    public void add() {
        var originalFilters = new HashMap<>(Map.of(
                "m", " AND m.medical_record_id = 15",
                "oD", " AND oD.facility LIKE 'something'"
        ));
        var queryMerger     = new BasicPreFilterQueryProcessor(originalFilters);
        var newFilter = queryMerger.update(" AND c.cohort_tag_name = 'OS PE-CGS'");
        var expectedQuery = " AND c.cohort_tag_name = 'OS PE-CGS'";
        var actual = newFilter.get("c");
        Assert.assertEquals(expectedQuery, actual);
    }

}
