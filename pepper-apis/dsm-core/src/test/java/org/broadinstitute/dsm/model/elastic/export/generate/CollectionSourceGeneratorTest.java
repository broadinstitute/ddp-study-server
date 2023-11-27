package org.broadinstitute.dsm.model.elastic.export.generate;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.patch.Patch;
import org.junit.Test;

public class CollectionSourceGeneratorTest {


    @Test
    public void generate() {
        ValueParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(PropertyInfo.of("m"));
        List<NameValue> nameValues =
                Arrays.asList(new NameValue("m.name", "Garden Grove Hospital & Medical Center"), new NameValue("m.phone", "123-456-7849"),
                        new NameValue("m.fax", "123-456-7849"));
        Patch patch = new Patch();
        patch.setId("1000");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValues, patch);
        CollectionSourceGenerator collectionSourceGenerator = new CollectionSourceGenerator(valueParser, generatorPayload);
        Map<String, Object> actual = collectionSourceGenerator.generate();

        Map<String, Object> expected = Map.of(SourceGenerator.DSM_OBJECT, Map.of("medicalRecord", List.of(new LinkedHashMap<>(
                Map.of("name", "Garden Grove Hospital & Medical Center", "fax", "123-456-7849", "phone", "123-456-7849", "medicalRecordId",
                        "1000")))));
        Object actualName = ((Map) ((List) (((Map) actual.get("dsm")).get("medicalRecord"))).get(0)).get("name");
        Object actualFax = ((Map) ((List) (((Map) actual.get("dsm")).get("medicalRecord"))).get(0)).get("fax");
        Object actualPhone = ((Map) ((List) (((Map) actual.get("dsm")).get("medicalRecord"))).get(0)).get("phone");
        Object expectedName = ((Map) ((List) (((Map) expected.get("dsm")).get("medicalRecord"))).get(0)).get("name");
        Object expectedFax = ((Map) ((List) (((Map) expected.get("dsm")).get("medicalRecord"))).get(0)).get("fax");
        Object expectedPhone = ((Map) ((List) (((Map) expected.get("dsm")).get("medicalRecord"))).get(0)).get("phone");

        assertEquals(Arrays.asList(actualName, actualFax, actualPhone), Arrays.asList(expectedName, expectedFax, expectedPhone));
    }

}
