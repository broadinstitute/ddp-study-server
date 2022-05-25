package org.broadinstitute.dsm.model.elastic.search;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.FollowUp;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.Assert;
import org.junit.Test;

public class SourceMapDeserializerTest {

    @Test
    public void getParameterizedType() throws NoSuchFieldException {
        class MockClass {
            List<Object> listField;
            FollowUp[] followUps;
            Object obj;
        }

        Field listField = MockClass.class.getDeclaredField("listField");
        Field followUps = MockClass.class.getDeclaredField("followUps");
        Field obj = MockClass.class.getDeclaredField("obj");

        SourceMapDeserializer sourceMapDeserializer = new SourceMapDeserializer();

        Class<?> clazz = null;
        try {
            clazz = sourceMapDeserializer.getParameterizedType(listField.getGenericType());
            assertEquals(Object.class, clazz);
            clazz = sourceMapDeserializer.getParameterizedType(followUps.getGenericType());
            assertEquals(FollowUp.class, clazz);
            clazz = sourceMapDeserializer.getParameterizedType(obj.getGenericType());
            assertEquals(Object.class, clazz);
        } catch (ClassNotFoundException e) {
            Assert.fail();
        }
    }

    @Test
    public void convertFollowUpsJsonToList() {
        Map<String, String> dynamicFields = Map.of(
                "registrationType", "Self",
                "registrationStatus", "Registered"
        );

        Map<String, Object> outerProperties = Map.of(
                "ddpInstanceId", 12,
                ESObjectConstants.DYNAMIC_FIELDS, dynamicFields
        );

        SourceMapDeserializer sourceMapDeserializer = new SourceMapDeserializer();
        sourceMapDeserializer.outerProperty = ESObjectConstants.PARTICIPANT_DATA;
        try {
            Map<String, Object> dynamicFieldsValueAsJson = ObjectMapperSingleton.instance()
                    .readValue(sourceMapDeserializer.getDynamicFieldsValueAsJson(outerProperties), Map.class);
            Assert.assertEquals(dynamicFieldsValueAsJson.get("REGISTRATION_TYPE"), "Self");
            Assert.assertEquals(dynamicFieldsValueAsJson.get("REGISTRATION_STATUS"), "Registered");
            Assert.assertEquals(12, outerProperties.get("ddpInstanceId"));
        } catch (IOException e) {
            Assert.fail();
        }
    }

    @Test
    public void testResultToJson() {

        SourceMapDeserializer sourceMapDeserializer = new SourceMapDeserializer();

        Map<Object, Object> testResultInner = new LinkedHashMap<>();
        testResultInner.put("isCorrected", true);
        testResultInner.put("timeCompleted", "2020-01-01");
        testResultInner.put("result", "Positive");

        Map<String, Object> testResult = Map.of("testResult", List.of(testResultInner));

        String testResultValueAsJson = sourceMapDeserializer.convertTestResultValueAsJson(testResult);

        String expected = "[{\"isCorrected\":true,\"timeCompleted\":\"2020-01-01\",\"result\":\"Positive\"}]";

        Assert.assertEquals(expected, testResultValueAsJson);
    }
}
