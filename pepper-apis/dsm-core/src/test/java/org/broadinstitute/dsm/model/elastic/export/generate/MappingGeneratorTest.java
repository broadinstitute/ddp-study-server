package org.broadinstitute.dsm.model.elastic.export.generate;


import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.export.TestPatchUtil;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.PROPERTIES;

public class MappingGeneratorTest {


    @Test
    public void generateTextType() {
        Map<String, Object> objectMap = TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.MEDICAL_RECORD_COLUMN, "value"
                , 0)).generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), PROPERTIES);
        String type = extractDeepestLeveleValue(objectMap, TestPatchUtil.MEDICAL_RECORD_COLUMN);
        Assert.assertEquals("text", type);
    }

    public static GeneratorPayload getGeneratorPayload(String columnName, Object value, int recordId) {
        return new GeneratorPayload(new NameValue(columnName, value),
                recordId) {
            @Override
            public String getCamelCaseFieldName() {
                return Util.underscoresToCamelCase(columnName);
            }
        };
    }

    @Test
    public void generateTextTypeWithFields() {
        Map<String, Object> objectMap = TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.MEDICAL_RECORD_COLUMN,"value"
                , 0)).generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), PROPERTIES);
        String type = extractKeywordType(objectMap, TestPatchUtil.MEDICAL_RECORD_COLUMN);
        Assert.assertEquals("keyword", type);
    }

    @Test
    public void generateBooleanType() {
        Map<String, Object> objectMap = TestCollectionMappingGenerator.of(getGeneratorPayload(TestPatchUtil.MR_PROBLEM,true,
                0)).generate();
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
        Map<String, Object> dsmLevelProperty = Map.of(generator.getPropertyName(), Map.of(
                MappingGenerator.TYPE, MappingGenerator.NESTED,
                PROPERTIES, Map.of(Util.underscoresToCamelCase(TestPatchUtil.DATE_FIELD), Map.of(MappingGenerator.TYPE, "date"))));
        Map<String, Object> dsmLevelProperties = Map.of(PROPERTIES, dsmLevelProperty);
        Map<String, Object> dsmLevel = Map.of(MappingGenerator.DSM_OBJECT, dsmLevelProperties);
        Map<String, Object> topLevel = Map.of(PROPERTIES, dsmLevel);
        Assert.assertTrue(topLevel.equals(resultMap));
    }

    @Test
    public void parseJson() {
        NameValue nameValue = new NameValue("m.additionalValuesJson", "{\"DDP_INSTANCE\": \"TEST\"}");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 0);
        DynamicFieldsParser parser = new DynamicFieldsParser();
        parser.setDisplayType("TEXT");
        parser.setParser(new TypeParser());
        parser.setPropertyInfo(new BaseGenerator.PropertyInfo(MedicalRecord.class, true));
        BaseGenerator mappingGenerator = new CollectionMappingGenerator(parser, generatorPayload);
        Map<String, Object> parseJson = mappingGenerator.parseJson();
        Map<String, Object> additionalValuesJson = (Map)parseJson.get("dynamicFields");
        Assert.assertNotNull(additionalValuesJson);
        Assert.assertEquals(TypeParser.TEXT_KEYWORD_MAPPING,((Map) additionalValuesJson.get(PROPERTIES)).get(Util.underscoresToCamelCase("DDP_INSTANCE")));
    }


    private String extractDeepestLeveleValue(Map<String, Object> objectMap, String field) {
        return (String)
                ((Map)
                ((Map)
                getMedicalRecordProperty(objectMap)
                        .get(PROPERTIES))
                        .get(Util.underscoresToCamelCase(field)))
                        .get("type");
    }

    private String extractKeywordType(Map<String, Object> objectMap, String field) {
        return (String)
                ((Map)
                ((Map)
                ((Map)
                ((Map)
                getMedicalRecordProperty(objectMap)
                        .get(PROPERTIES))
                        .get(Util.underscoresToCamelCase(field)))
                        .get("fields"))
                        .get("keyword"))
                        .get("type");
    }

    private Map getMedicalRecordProperty(Map<String, Object> objectMap) {
        return (Map)
                ((Map)
                ((Map)
                ((Map) objectMap
                        .get(PROPERTIES))
                        .get(BaseGenerator.DSM_OBJECT))
                        .get(PROPERTIES))
                        .get("medicalRecord");
    }


    private static class TestSingleMappingGenerator extends SingleMappingGenerator {

        public TestSingleMappingGenerator(Parser parser, GeneratorPayload generatorPayload) {
            super(parser, generatorPayload);
        }

        @Override
        protected DBElement getDBElement() {
            return TestPatchUtil.getColumnNameMap().get(getNameValue().getName());
        }

        public static TestSingleMappingGenerator of(GeneratorPayload generatorPayload) {
            return new TestSingleMappingGenerator(new TypeParser(), generatorPayload);
        }

    }


    private static class TestCollectionMappingGenerator extends CollectionMappingGenerator {

        public TestCollectionMappingGenerator(Parser typeParser, GeneratorPayload generatorPayload) {
            super(typeParser, generatorPayload);
        }

        @Override
        protected DBElement getDBElement() {
            return TestPatchUtil.getColumnNameMap().get(getNameValue().getName());
        }

        public static TestCollectionMappingGenerator of(GeneratorPayload generatorPayload) {
            BaseParser typeParser = new TypeParser();
            typeParser.setPropertyInfo(new PropertyInfo(TestPropertyClass.class, true));
            return new TestCollectionMappingGenerator(typeParser, generatorPayload);
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