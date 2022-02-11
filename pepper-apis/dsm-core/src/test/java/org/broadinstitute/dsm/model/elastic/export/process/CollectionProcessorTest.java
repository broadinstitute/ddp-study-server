package org.broadinstitute.dsm.model.elastic.export.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.export.TestPatchUtil;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.CollectionSourceGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Collector;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGeneratorTest;
import org.broadinstitute.dsm.model.elastic.export.generate.SourceGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.elastic.export.process.CollectionProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CollectionProcessorTest {

    @Test
    public void testProcess() throws IOException {
        String propertyName = "medicalRecord";
        double recordId = 5;
        String oldValue = "mr_old";
        String json = String.format("{\"%s\":[{\"medicalRcordId\":%s,\"mrProblemText\":\"%s\"}]}", propertyName, recordId, oldValue);

        ObjectMapper objectMapper = new ObjectMapper();

        ESDsm esDsm = objectMapper.readValue(json, ESDsm.class);

        NameValue nameValue = new NameValue("m.mrProblemText", "mr_updated");

        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 5);

        BaseParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new BaseGenerator.PropertyInfo(MedicalRecord.class, true));

        Processor collectionProcessor = new TestCollectionProcessor(esDsm, propertyName, generatorPayload,
                new CollectionSourceGenerator(valueParser, generatorPayload));

        List<Map<String, Object>> updatedList = (List<Map<String, Object>>) collectionProcessor.process();

        Map<String, Object> updatedObject = updatedList.get(0);
        Map<String, Object> oldObject = ((Map) ((List) objectMapper.readValue(json, Map.class).get(propertyName)).get(0));

        Assert.assertNotEquals(oldObject, updatedObject);

    }

    @Test
    public void updateIfExistsOrPut() throws IOException {
        String propertyName = "medicalRecord";
        double recordId = 5;
        String json = String.format("{\"%s\":[{\"medicalRcordId\":%s,\"mrProblemText\":\"%s\"}]}", propertyName, recordId, "value");;

        ObjectMapper objectMapper = new ObjectMapper();

        ESDsm esDsm = objectMapper.readValue(json, ESDsm.class);

        NameValue nameValue = new NameValue("m.mrProblemText", "val");

        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 10);

        BaseParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new BaseGenerator.PropertyInfo(MedicalRecord.class, true));

        CollectionProcessor collectionProcessor = new TestCollectionProcessor(esDsm, propertyName, generatorPayload,
                new CollectionSourceGenerator(valueParser, generatorPayload));

        List<Map<String, Object>> updatedList = collectionProcessor.process();

        Assert.assertEquals(2, updatedList.size());

    }

    @Test
    public void updateIfExists() throws IOException {
        String propertyName = "medicalRecord";
        double recordId = 5;
        String json = String.format("{\"%s\":[{\"medicalRecordId\":%s,\"type\":\"%s\", \"mrProblemText\":\"TEST_VAL2\"}]}", propertyName, recordId, "value");;

        ObjectMapper objectMapper = new ObjectMapper();

        ESDsm esDsm = objectMapper.readValue(json, ESDsm.class);

        NameValue nameValue = new NameValue("m.type", "TEST_VAL");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, 5);

        BaseParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new BaseGenerator.PropertyInfo(MedicalRecord.class, true));

        CollectionProcessor collectionProcessor = new TestCollectionProcessor(esDsm, propertyName, generatorPayload,
                new CollectionSourceGenerator(valueParser, generatorPayload));


        List<Map<String, Object>> updatedList = collectionProcessor.process();

        updatedList.stream()
                .filter(i -> i.containsKey("type"))
                .findFirst()
                .ifPresentOrElse(m -> Assert.assertEquals("TEST_VAL", m.get("type")), Assert::fail);

        updatedList.stream()
                .filter(i -> i.containsKey("mrProblemText"))
                .findFirst()
                .ifPresentOrElse(m -> Assert.assertEquals("TEST_VAL2", m.get("mrProblemText")), Assert::fail);
    }

    private static class TestCollectionProcessor extends CollectionProcessor {


        public TestCollectionProcessor(ESDsm esDsm, String propertyName, GeneratorPayload generatorPayload, Collector collector) {
            super(esDsm, propertyName, generatorPayload.getRecordId(), collector);
        }

    }

}

