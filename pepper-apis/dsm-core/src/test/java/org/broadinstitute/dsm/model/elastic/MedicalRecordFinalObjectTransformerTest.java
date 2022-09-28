
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
                Map.of("datePx2", Map.of("dateString", "2020-01-01", "est", "true"));
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformTextToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "text", "I love DSM", 5L,
                6L, "Text Field", 10));
        var expectedDynamicFields = Map.of("textField10", "I love DSM");
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformTextAreaToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "text_area", "I love DSS", 5L,
                6L, "Text Field", 1));
        var expectedDynamicFields = Map.of("textField1", "I love DSS");
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

}
