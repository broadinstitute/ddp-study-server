package org.broadinstitute.dsm.model.elastic.export.generate;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.junit.Test;

public class CollectionMappingGeneratorTest {


    @Test
    public void generate() {
        TypeParser typeParser = new TypeParser();
        typeParser.setPropertyInfo(PropertyInfo.of("m"));
        List<NameValue> nameValues =
                Arrays.asList(new NameValue("m.name", "Garden Grove Hospital & Medical Center"), new NameValue("m.phone", "123-456-7849"),
                        new NameValue("m.fax", "123-456-7849"));
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValues, null);
        CollectionMappingGenerator collectionMappingGenerator = new CollectionMappingGenerator(typeParser, generatorPayload);
        Map<String, Object> actual = collectionMappingGenerator.generate();
        Map<Object, Object> fieldsLevel = Map.of("name", TypeParser.TEXT_KEYWORD_MAPPING, "phone", TypeParser.TEXT_KEYWORD_MAPPING, "fax",
                TypeParser.TEXT_KEYWORD_MAPPING);
        Map<String, Object> medicalRecord =
                Map.of("medicalRecord", Map.of(MappingGenerator.TYPE, MappingGenerator.NESTED, MappingGenerator.PROPERTIES, fieldsLevel));
        Map<String, Object> dsmLevelProperties = Map.of(BaseGenerator.PROPERTIES, medicalRecord);
        Map<String, Object> dsmLevel = Map.of(BaseGenerator.DSM_OBJECT, dsmLevelProperties);
        Map<String, Object> expected = Map.of(BaseGenerator.PROPERTIES, dsmLevel);
        assertEquals(expected, actual);
    }

}
