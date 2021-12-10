package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.TestPatchUtil;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SourceGeneratorTest {

    @Test
    public void generateCollection() {
        NameValue nameValue = new NameValue(TestPatchUtil.MEDICAL_RECORD_COLUMN, "value");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 0);
        Generator generator = new TestSourceGenerator(new ValueParser(), generatorPayload);
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);
        List<Map<String, Object>> medicalRecords = (List) ((Map) objectMap.get(SourceGenerator.DSM_OBJECT)).get("medicalRecords");
        Optional<Map<String, Object>> first = medicalRecords.stream()
                .filter(i -> i.get(TestPatchUtil.MEDICAL_RECORD_COLUMN) != null)
                .findFirst();
        first.ifPresentOrElse(val -> Assert.assertEquals("value", val.get(TestPatchUtil.MEDICAL_RECORD_COLUMN)), Assert::fail);
    }

    @Test
    public void generateNumeric() {
        NameValue nameValue = new NameValue(TestPatchUtil.MEDICAL_RECORD_COLUMN, "1");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 0);
        Generator generator = new TestSourceGenerator(new ValueParser(), generatorPayload);
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);
        List<Map<String, Object>> medicalRecords = (List) ((Map) objectMap.get(SourceGenerator.DSM_OBJECT)).get("medicalRecords");
        Optional<Map<String, Object>> first = medicalRecords.stream()
                .filter(i -> i.get(TestPatchUtil.MEDICAL_RECORD_COLUMN) != null)
                .findFirst();
        first.ifPresentOrElse(val -> Assert.assertEquals(1L, val.get(TestPatchUtil.MEDICAL_RECORD_COLUMN)), Assert::fail);
    }

    @Test
    public void generateFromJson() {
        NameValue nameValue = new NameValue(TestPatchUtil.TISSUE_RECORD_COLUMN, "{\"DDP_INSTANCE\": \"TEST\", \"DPP_VALUE\": \"VALUE\"}");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 0);
        Generator generator = new TestSourceGenerator(new ValueParser(), generatorPayload);
        Map<String, Object> objectMap = generator.generate();
        Assert.assertEquals(objectMap.keySet().stream().findFirst().get(), SourceGenerator.DSM_OBJECT);

        List<Map<String, Object>> tissueRecords = (List<Map<String, Object>>) ((Map<String, Object>) objectMap
                .get(SourceGenerator.DSM_OBJECT))
                .get("tissueRecords");
        Optional<Map<String, Object>> maybeDdpInstance = tissueRecords.stream()
                .filter(i -> i.get("DDP_INSTANCE") != null)
                .findFirst();
        maybeDdpInstance.ifPresentOrElse(m -> Assert.assertEquals("TEST", m.get("DDP_INSTANCE")), Assert::fail);
    }

    private static class TestSourceGenerator extends SourceGenerator {

        public TestSourceGenerator(Parser parser, GeneratorPayload generatorPayload) {
            super(parser,generatorPayload);
        }

        @Override
        protected DBElement getDBElement() {
            return TestPatchUtil.getColumnNameMap().get(getNameValue().getName());
        }
    }

}

