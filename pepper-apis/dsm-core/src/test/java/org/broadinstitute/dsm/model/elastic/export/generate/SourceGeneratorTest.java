package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.export.TestPatchUtil;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Test;
import org.junit.Assert;

public class SourceGeneratorTest {

    @Test
    public void generateCollection() {
        BaseParser parser = new ValueParser();
        parser.setPropertyInfo(new BaseGenerator.PropertyInfo(MappingGeneratorTest.TestPropertyClass.class, true));
        Generator generator = new TestSourceGenerator(parser, getGeneratorPayload(TestPatchUtil.MEDICAL_RECORD_COLUMN, "value"
                , 0));
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);
        List<Map<String, Object>> medicalRecords = (List) ((Map) objectMap.get(SourceGenerator.DSM_OBJECT)).get("medicalRecord");
        Optional<Map<String, Object>> first = medicalRecords.stream()
                .filter(i -> i.get(Util.underscoresToCamelCase(TestPatchUtil.MEDICAL_RECORD_COLUMN)) != null)
                .findFirst();
        first.ifPresentOrElse(val -> Assert.assertEquals("value", val.get(Util.underscoresToCamelCase(TestPatchUtil.MEDICAL_RECORD_COLUMN))), Assert::fail);
    }

    private GeneratorPayload getGeneratorPayload(String columnName, Object value, int recordId) {
        return new GeneratorPayload(new NameValue(columnName, value),
                recordId) {
            @Override
            public String getCamelCaseFieldName() {
                return Util.underscoresToCamelCase(columnName);
            }
        };
    }

    @Test
    public void generateNumeric() {
        BaseParser parser = new ValueParser();
        parser.setPropertyInfo(new BaseGenerator.PropertyInfo(MappingGeneratorTest.TestPropertyClass.class, true));
        Generator generator = new TestSourceGenerator(parser, getGeneratorPayload(TestPatchUtil.NUMERIC_FIELD, 1, 0));
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);
        List<Map<String, Object>> medicalRecords = (List) ((Map) objectMap.get(SourceGenerator.DSM_OBJECT)).get("medicalRecord");
        Optional<Map<String, Object>> first = medicalRecords.stream()
                .filter(i -> i.get(Util.underscoresToCamelCase(TestPatchUtil.NUMERIC_FIELD)) != null)
                .findFirst();
        first.ifPresentOrElse(val -> Assert.assertEquals(1L, val.get(Util.underscoresToCamelCase(TestPatchUtil.NUMERIC_FIELD))), Assert::fail);
    }

    @Test
    public void generateFromJson() {
        NameValue nameValue = new NameValue("additional_values_json", "{\"DDP_INSTANCE\": \"TEST\", \"DDP_VALUE\": \"VALUE\"}");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 0);
        DynamicFieldsParser dynamicFieldsParser = new TestDynamicFieldsParser();
        dynamicFieldsParser.setParser(new ValueParser());
        dynamicFieldsParser.setPropertyInfo(new BaseGenerator.PropertyInfo(MedicalRecord.class, true));
        Generator generator = new TestSourceGenerator(dynamicFieldsParser, generatorPayload);
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);

        List<Map<String, Object>> medicalRecord =
                (List<Map<String, Object>>) ((Map<String, Object>) objectMap.get(SourceGenerator.DSM_OBJECT)).get("medicalRecord");
        Map<String, Object> dynamicFields = (Map<String, Object>) medicalRecord.get(0).get("dynamicFields");
        Assert.assertEquals("TEST", dynamicFields.get("ddpInstance"));
        Assert.assertEquals("VALUE", dynamicFields.get("ddpValue"));
    }



    private static class TestSourceGenerator extends CollectionSourceGenerator {

        public TestSourceGenerator(Parser parser, GeneratorPayload generatorPayload) {
            super(parser,generatorPayload);
        }

        @Override
        protected DBElement getDBElement() {
            return TestPatchUtil.getColumnNameMap().get(getNameValue().getName());
        }
    }

    public static class TestDynamicFieldsParser extends DynamicFieldsParser {

        @Override
        protected void getProperDisplayTypeWithPossibleValues() {
            displayType = StringUtils.EMPTY;
        }
    }

}

