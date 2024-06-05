package org.broadinstitute.dsm.model.elastic.export.generate;


import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.PROPERTIES;

import java.util.Map;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.TestPatchUtil;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.model.patch.Patch;
import org.junit.Assert;
import org.junit.Test;

public class MappingGeneratorTest {


    public static GeneratorPayload getGeneratorPayload(String columnName, Object value, int recordId) {
        Patch patch = new Patch();
        patch.setId(String.valueOf(recordId));
        return new GeneratorPayload(new NameValue(columnName, value), patch) {
            @Override
            public String getCamelCaseFieldName() {
                return CamelCaseConverter.of(columnName).convert();
            }
        };
    }

    @Test
    public void generateTextType() {
        Map<String, Object> objectMap =
                TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.MEDICAL_RECORD_COLUMN,
                        "value", 0)).generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), PROPERTIES);
        String type = extractDeepestLeveleValue(objectMap, TestPatchUtil.MEDICAL_RECORD_COLUMN);
        Assert.assertEquals("text", type);
    }

    @Test
    public void generateTextTypeWithFields() {
        Map<String, Object> objectMap =
                TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.MEDICAL_RECORD_COLUMN,
                        "value", 0)).generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), PROPERTIES);
        String type = extractKeywordType(objectMap, TestPatchUtil.MEDICAL_RECORD_COLUMN);
        Assert.assertEquals("keyword", type);
    }

    @Test
    public void generateBooleanType() {
        Map<String, Object> objectMap =
                TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.MR_PROBLEM, true, 0)).generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), PROPERTIES);
        String type = extractDeepestLeveleValue(objectMap, TestPatchUtil.MR_PROBLEM);
        Assert.assertEquals("boolean", type);
    }

    @Test
    public void generateDateType() {
        Map<String, Object> objectMap =
                TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.DATE_FIELD, "2021-10-30", 0)).generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), PROPERTIES);
        String type = extractDeepestLeveleValue(objectMap, TestPatchUtil.DATE_FIELD);
        Assert.assertEquals("date", type);
    }

    @Test
    public void generateNestedType() {
        Map<String, Object> objectMap =
                TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.DATE_FIELD, "2021-10-30", 100)).generate();
        Assert.assertEquals(MappingGenerator.NESTED, getMedicalRecordProperty(objectMap).get(MappingGenerator.TYPE));
    }

    @Test
    public void generateMapping() {
        MappingGenerator generator = TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.DATE_FIELD, "2021-10-30", 100));
        Map<String, Object> resultMap = generator.generate();
        Map<String, Object> dsmLevelProperty = Map.of(generator.getPropertyName(),
                Map.of(MappingGenerator.TYPE, MappingGenerator.NESTED, PROPERTIES,
                        Map.of(CamelCaseConverter.of(TestPatchUtil.DATE_FIELD).convert(), Map.of(MappingGenerator.TYPE, "date"))));
        Map<String, Object> dsmLevelProperties = Map.of(PROPERTIES, dsmLevelProperty);
        Map<String, Object> dsmLevel = Map.of(MappingGenerator.DSM_OBJECT, dsmLevelProperties);
        Map<String, Object> topLevel = Map.of(PROPERTIES, dsmLevel);
        Assert.assertTrue(topLevel.equals(resultMap));
    }

    @Test
    public void parseJson() {
        try {
            NameValue nameValue = new NameValue("m.additionalValuesJson", "{\"DDP_INSTANCE\": \"TEST\"}");
            Patch patch = new Patch();
            patch.setId("0");
            GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, patch);
            DynamicFieldsParser parser = new DynamicFieldsParser();
            parser.setDisplayType("TEXT");
            parser.setHelperParser(new TypeParser());
            parser.setPropertyInfo(new PropertyInfo(MedicalRecord.class, true));
            BaseGenerator mappingGenerator = new CollectionMappingGenerator(parser, generatorPayload);
            mappingGenerator.setFieldTypeExtractor(new FieldTypeExtractor() {
                @Override
                public Map<String, String> extract() {
                    return Map.of();
                }
            });
            Map<String, Object> parseJson = mappingGenerator.parseJson();
            ;
            Map<String, Object> additionalValuesJson = (Map) parseJson.get("dynamicFields");
            Assert.assertNotNull(additionalValuesJson);
            Assert.assertEquals(TypeParser.TEXT_KEYWORD_MAPPING,
                    ((Map) additionalValuesJson.get(PROPERTIES)).get(CamelCaseConverter.of("DDP_INSTANCE").convert()));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception");
        }
    }


    private String extractDeepestLeveleValue(Map<String, Object> objectMap, String field) {
        return (String) ((Map) ((Map) getMedicalRecordProperty(objectMap).get(PROPERTIES)).get(CamelCaseConverter.of(field).convert())).get(
                "type");
    }

    private String extractKeywordType(Map<String, Object> objectMap, String field) {
        return (String) ((Map) ((Map) ((Map) ((Map) getMedicalRecordProperty(objectMap).get(PROPERTIES)).get(
                CamelCaseConverter.of(field).convert())).get("fields")).get("keyword")).get("type");
    }

    private Map getMedicalRecordProperty(Map<String, Object> objectMap) {
        return (Map) ((Map) ((Map) ((Map) objectMap.get(PROPERTIES)).get(BaseGenerator.DSM_OBJECT)).get(PROPERTIES)).get("medicalRecord");
    }


    private static class TestSingleMappingGenerator extends SingleMappingGenerator {

        public TestSingleMappingGenerator(Parser parser, GeneratorPayload generatorPayload) {
            super(parser, generatorPayload);
        }

        public static TestSingleMappingGenerator of(GeneratorPayload generatorPayload) {
            return new TestSingleMappingGenerator(new TypeParser(), generatorPayload);
        }

        @Override
        protected DBElement getDBElement() {
            return TestPatchUtil.getColumnNameMap().get(getNameValue().getName());
        }

    }


    private static class TestCollectionMappingGenerator extends CollectionMappingGenerator {

        public TestCollectionMappingGenerator(Parser typeParser, GeneratorPayload generatorPayload) {
            super(typeParser, generatorPayload);
        }

        public static TestCollectionMappingGenerator of(GeneratorPayload generatorPayload) {
            BaseParser typeParser = new TypeParser();
            typeParser.setPropertyInfo(new PropertyInfo(TestPropertyClass.class, true));
            return new TestCollectionMappingGenerator(typeParser, generatorPayload);
        }

        @Override
        protected DBElement getDBElement() {
            return TestPatchUtil.getColumnNameMap().get(getNameValue().getName());
        }
    }

    public static class TestPropertyClass {
        String medicalRecordColumn;
        String tissueRecordColumn;
        boolean mrProblem;
        @DbDateConversion(SqlDateConverter.STRING_DAY)
        String dateField;
        long numericField;
    }
}
