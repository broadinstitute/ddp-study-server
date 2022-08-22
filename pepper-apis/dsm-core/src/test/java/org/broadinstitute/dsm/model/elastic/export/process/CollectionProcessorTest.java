package org.broadinstitute.dsm.model.elastic.export.process;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.export.generate.CollectionSourceGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Collector;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.model.patch.Patch;
import org.junit.Assert;
import org.junit.Test;

public class CollectionProcessorTest {

    @Test
    public void testProcess() throws IOException {
        String propertyName = "medicalRecord";
        double recordId = 5;
        String oldValue = "mr_old";
        String json = String.format("{\"%s\":[{\"medicalRcordId\":%s,\"mrProblemText\":\"%s\"}]}", propertyName, recordId, oldValue);

        ObjectMapper objectMapper = new ObjectMapper();

        Dsm esDsm = objectMapper.readValue(json, Dsm.class);

        NameValue nameValue = new NameValue("m.mrProblemText", "mr_updated");

        Patch patch = new Patch();
        patch.setId("5");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, patch);

        BaseParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new PropertyInfo(MedicalRecord.class, true));

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
        String json = String.format("{\"%s\":[{\"medicalRcordId\":%s,\"mrProblemText\":\"%s\"}]}", propertyName, recordId, "value");
        ;

        ObjectMapper objectMapper = new ObjectMapper();

        Dsm esDsm = objectMapper.readValue(json, Dsm.class);

        NameValue nameValue = new NameValue("m.mrProblemText", "val");

        Patch patch = new Patch();
        patch.setId("10");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, patch);

        BaseParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new PropertyInfo(MedicalRecord.class, true));

        CollectionProcessor collectionProcessor = new TestCollectionProcessor(esDsm, propertyName, generatorPayload,
                new CollectionSourceGenerator(valueParser, generatorPayload));

        List<Map<String, Object>> updatedList = collectionProcessor.process();

        Assert.assertEquals(2, updatedList.size());

    }

    @Test
    public void updateIfExists() throws IOException {
        String propertyName = "medicalRecord";
        double recordId = 5;
        String json = String.format("{\"%s\":[{\"medicalRecordId\":%s,\"type\":\"%s\", \"mrProblemText\":\"TEST_VAL2\"}]}", propertyName,
                recordId, "value");


        ObjectMapper objectMapper = new ObjectMapper();

        Dsm esDsm = objectMapper.readValue(json, Dsm.class);

        NameValue nameValue = new NameValue("m.type", "TEST_VAL");
        Patch patch = new Patch();
        patch.setId("5");
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValue, patch);

        BaseParser valueParser = new ValueParser();
        valueParser.setPropertyInfo(new PropertyInfo(MedicalRecord.class, true));

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


        public TestCollectionProcessor(Dsm dsm, String propertyName, GeneratorPayload generatorPayload, Collector collector) {
            super(dsm, propertyName, generatorPayload.getRecordId(), collector);
        }

    }

}

