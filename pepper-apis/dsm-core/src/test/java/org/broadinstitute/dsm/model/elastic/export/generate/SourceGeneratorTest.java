package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.TestPatchUtil;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.patch.Patch;
import org.junit.Assert;
import org.junit.Test;

public class SourceGeneratorTest {

    @Test
    public void generateCollection() {
        BaseParser parser = new ValueParser();
        parser.setPropertyInfo(new PropertyInfo(MappingGeneratorTest.TestPropertyClass.class, true));
        Generator generator = new TestSourceGenerator(parser, getGeneratorPayload(TestPatchUtil.MEDICAL_RECORD_COLUMN, "value", 0));
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);
        List<Map<String, Object>> medicalRecords = (List) ((Map) objectMap.get(SourceGenerator.DSM_OBJECT)).get("medicalRecord");
        Optional<Map<String, Object>> first =
                medicalRecords.stream().filter(i -> i.get(CamelCaseConverter.of(TestPatchUtil.MEDICAL_RECORD_COLUMN).convert()) != null)
                        .findFirst();
        first.ifPresentOrElse(
                val -> Assert.assertEquals("value", val.get(CamelCaseConverter.of(TestPatchUtil.MEDICAL_RECORD_COLUMN).convert())),
                Assert::fail);
    }

    private GeneratorPayload getGeneratorPayload(String columnName, Object value, int recordId) {
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
    public void generateNumeric() {
        BaseParser parser = new ValueParser();
        parser.setPropertyInfo(new PropertyInfo(MappingGeneratorTest.TestPropertyClass.class, true));
        Generator generator = new TestSourceGenerator(parser, getGeneratorPayload(TestPatchUtil.NUMERIC_FIELD, 1, 0));
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);
        List<Map<String, Object>> medicalRecords = (List) ((Map) objectMap.get(SourceGenerator.DSM_OBJECT)).get("medicalRecord");
        Optional<Map<String, Object>> first =
                medicalRecords.stream()
                        .filter(i -> i.get(CamelCaseConverter.of(TestPatchUtil.NUMERIC_FIELD).convert()) != null).findFirst();
        first.ifPresentOrElse(val -> Assert.assertEquals(1L, val.get(CamelCaseConverter.of(TestPatchUtil.NUMERIC_FIELD).convert())),
                Assert::fail);
    }

    @Test
    public void generateFromJson() {
        NameValue nameValue = new NameValue("additional_values_json", "{\"DDP_INSTANCE\": \"TEST\", \"DDP_VALUE\": \"VALUE\"}");
        Patch patch = new Patch();
        patch.setId("0");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, patch);
        DynamicFieldsParser dynamicFieldsParser = new TestDynamicFieldsParser();
        dynamicFieldsParser.setHelperParser(new ValueParser());
        dynamicFieldsParser.setPropertyInfo(new PropertyInfo(MedicalRecord.class, true));
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
            super(parser, generatorPayload);
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
