
package org.broadinstitute.dsm.model.elastic;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class MedicalRecordFinalObjectTransformerTest {

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
    }

    @Test
    public void transformSimpleMultiTypeArrayToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "multi_type_array",
                "[{\"Date of Procedure\":null,\"Type of Procedure\":\"Biopsy\",\"TNM Classification\":\"p\","
                        + "\"T\":\"2b\",\"N\":\"1\",\"M\":\"X\",\"Notes\":\"guessing\"}]",
                5L, 6L, "TNM", 1));
        var expectedDynamicFields = Map.of("tnm", Map.of(
                "typeOfProcedure", "Biopsy",
                "tnmClassification", "p",
                "notes", "guessing",
                "t", "2b",
                "m", "X",
                "n", "1"));
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformComplexMultiTypeArrayToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "multi_type_array",
                "[{\"SxBx Date\":\"{\\\"dateString\\\":\\\"2022-09-28\\\",\\\"est\\\":true}\",\"SxBx Type\":\"FNA\","
                        + "\"Location of SxBx\":\"Liver\",\"Tumor Size\":\"awd3\",\"Margin Post Sx\":\"Negative\","
                        + "\"Margin Notes\":\"awdwad\"," + "\"Path Results\":[\"DCIS\",\"other\"],\"Notes\":\"note\","
                        + "\"other\":{\"SxBx Type\":null,\"Location of SxBx\":null,\"Margin Post Sx\":null,"
                        + "\"Path Results\":\"Other\"}},{\"SxBx Date\":\"{\\\"dateString\\\":\\\"2022-09-28\\\","
                        + "\\\"est\\\":true}\",\"SxBx Type\":\"Core\",\"Location of SxBx\":\"other\",\"Tumor Size\":\"23\","
                        + "\"Margin Post Sx\":\"other\",\"Margin Notes\":\"33\",\"Path Results\":[\"LCIS\"],\"Notes\":\"ddd\","
                        + "\"other\":{\"SxBx Type\":null,\"Location of SxBx\":\"awdawd\","
                        + "\"Margin Post Sx\":\"Other\",\"Path Results\":null}}]",
                5L, 6L, "SxBx", 1));
        var expectedDynamicFields = Map.of("sxbx", Map.of(
                "sxbxDate", Map.of("dateString", "2022-09-28", "est", true),
                "pathResults", Map.of("values", List.of("DCIS", "other")),
                "notes", "note",
                "sxbxType", "FNA",
                "marginPostSx", "Negative",
                "tumorSize", "awd3",
                "marginNotes", "awdwad",
                "locationOfSxbx", "Liver"));
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }


}
