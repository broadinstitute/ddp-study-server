
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
    public void transformSingleDateToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "date", "2020-01-01", 5L,
                6L, "Date PX", 2));
        var expectedDynamicFields = Map.of("datePx", "2020-01-01");
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformComplexDateToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "date", "{\"dateString\": \"2020-01-01\", \"est\": \"true\"}", 5L,
                6L, "Date PX", 2));
        var expectedDynamicFields =
                Map.of("datePx", Map.of("dateString", "2020-01-01", "est", "true"));
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformTextToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "text", "I love DSM", 5L,
                6L, "Text Field", 10));
        var expectedDynamicFields = Map.of("textField", "I love DSM");
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformTextAreaToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "textarea", "I love DSS", 5L,
                6L, "Text Field", 1));
        var expectedDynamicFields = Map.of("textField", "I love DSS");
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformButtonSelectToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "button_select", "Pre-menopausal", 5L,
                6L, "Histology", 1));
        var expectedDynamicFields = Map.of("histology", "Pre-menopausal");
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformNumberToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "number", "100", 5L,
                6L, "DX Percent ER", 1));
        var expectedDynamicFields = Map.of("dxPercentEr", 100L);
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformSimpleMultiOptionsToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "multi_options", "[Bones, Cones]", 5L,
                6L, "Met sites at Mets dx", 1));
        var expectedDynamicFields = Map.of("metSitesAtMetsDx", Map.of("values", List.of("Bones", "Cones")));
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformComplexMultiOptionsToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "multi_options", "{\"other\":\"bones and some other as well\",\"Met sites at Mets dx\":[\" Bone\",\"other\"]}", 5L,
                6L, "Met sites at Mets dx", 1));
        var expectedDynamicFields = Map.of("metSitesAtMetsDx", Map.of("other", "bones and some other as well", "values", List.of(" Bone", "other")));
        Assert.assertEquals(expectedDynamicFields, actual.get("dynamicFields"));
    }

    @Test
    public void transformSimpleMultiTypeArrayToMap() {
        var transformer = new MedicalRecordFinalObjectTransformer("Prostate");
        var actual = transformer.transformObjectToMap(new MedicalRecordFinalDto(1L, 2L,
                3L, "multi_type_array",
                "[{\"Date of Procedure\":null,\"Type of Procedure\":\"Biopsy\",\"TNM Classification\":\"p\",\"T\":\"2b\",\"N\":\"1\",\"M\":\"X\",\"Notes\":\"guessing\"}]",
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
                "[{\"SxBx Date\":\"{\\\"dateString\\\":\\\"2022-09-28\\\",\\\"est\\\":true}\",\"SxBx Type\":\"FNA\",\"Location of SxBx\":\"Liver\",\"Tumor Size\":\"awd3\",\"Margin Post Sx\":\"Negative\",\"Margin Notes\":\"awdwad\",\"Path Results\":[\"DCIS\",\"other\"],\"Notes\":\"note\",\"other\":{\"SxBx Type\":null,\"Location of SxBx\":null,\"Margin Post Sx\":null,\"Path Results\":\"Other\"}},{\"SxBx Date\":\"{\\\"dateString\\\":\\\"2022-09-28\\\",\\\"est\\\":true}\",\"SxBx Type\":\"Core\",\"Location of SxBx\":\"other\",\"Tumor Size\":\"23\",\"Margin Post Sx\":\"other\",\"Margin Notes\":\"33\",\"Path Results\":[\"LCIS\"],\"Notes\":\"ddd\",\"other\":{\"SxBx Type\":null,\"Location of SxBx\":\"awdawd\",\"Margin Post Sx\":\"Other\",\"Path Results\":null}}]",
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
