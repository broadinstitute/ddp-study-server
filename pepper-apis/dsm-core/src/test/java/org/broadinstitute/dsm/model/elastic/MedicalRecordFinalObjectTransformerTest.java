
package org.broadinstitute.dsm.model.elastic;

import java.util.Map;

import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.junit.Assert;
import org.junit.Test;

public class MedicalRecordFinalObjectTransformerTest {

    @Test
    public void transformSingleDateToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "date", "2020-01-01", 5L,
                6L, "Date PX", 2));
        var expectedDynamicFields = Map.of("datePx2", "2020-01-01");
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformComplexDateToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "date", "{\"dateString\": \"2020-01-01\", \"est\": \"true\"}", 5L,
                6L, "Date PX", 2));
        var expectedDynamicFields =
                Map.of("datePx2", Map.of("dynamicFields", Map.of("dateString", "2020-01-01", "est", "true")));
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

}
