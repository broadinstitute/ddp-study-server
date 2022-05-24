package org.broadinstitute.dsm.model.elastic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.FollowUp;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UtilTest {

    @Before
    public void setUp() {
        FieldSettingsDao.setInstance(getMockFieldSettingsDao());
    }

    public static FieldSettingsDao getMockFieldSettingsDao() {
        class FieldSettingsDaoMock extends FieldSettingsDao {

            @Override
            public Optional<FieldSettingsDto> getFieldSettingsByInstanceNameAndColumnName(String instanceName, String columnName) {
                FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(0);
                if ("BOOLEAN_VAL".equals(columnName)) {
                    builder.withDisplayType("CHECKBOX");
                } else if ("LONG_VAL".equals(columnName)) {
                    builder.withDisplayType("NUMBER");
                }
                return Optional.of(builder.build());
            }
        }

        return new FieldSettingsDaoMock();
    }

    @Test
    public void underscoresToCamelCase() {
        String fieldName = "column_name";
        String fieldName2 = "COLUMN_NAME";
        String fieldName3 = "column";
        String fieldName4 = "COLUMN";
        String fieldName5 = "columnName";
        String transformed = Util.underscoresToCamelCase(fieldName);
        String transformed2 = Util.underscoresToCamelCase(fieldName2);
        String transformed3 = Util.underscoresToCamelCase(fieldName3);
        String transformed4 = Util.underscoresToCamelCase(fieldName4);
        String transformed5 = Util.underscoresToCamelCase(fieldName5);
        assertEquals("columnName", transformed);
        assertEquals("columnName", transformed2);
        assertEquals("column", transformed3);
        assertEquals("column", transformed4);
        assertEquals("columnName", transformed5);
    }


    @Test
    public void transformObjectToMap() {
        Participant participant = new Participant(
                1L, "QWERTY", null,
                null, "instance", "2020-10-28",
                "2020-10-28", "2020-10-28", "2020-10-28",
                "ptNotes", true, true,
                "additionalValuesJson", 1934283746283L);
        Map<String, Object> transformedObject = new ObjectTransformer().transformObjectToMap(participant, StringUtils.EMPTY);
        assertEquals(1L, transformedObject.get("participantId"));
        assertEquals("QWERTY", transformedObject.get("ddpParticipantId"));
        assertEquals("2020-10-28", transformedObject.get("created"));
        assertEquals(true, transformedObject.get("minimalMr"));
        assertEquals(1934283746283L, transformedObject.get("exitDate"));
    }

    @Test
    public void transformJsonToMap() {

        setUp();

        String json = "{\"DDP_INSTANCE\": \"TEST\", \"DDP_VALUE\": \"VALUE\", \"BOOLEAN_VAL\": \"true\", \"LONG_VAL\": \"5\"}";

        ParticipantData participantData = new ParticipantData.Builder()
                .withParticipantDataId(10)
                .withDdpParticipantId("123")
                .withDdpInstanceId(55)
                .withFieldTypeId("f")
                .withData(json)
                .build();
        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setHelperParser(new ValueParser());
        Map<String, Object> result = new ObjectTransformer(dynamicFieldsParser).transformObjectToMap(participantData, StringUtils.EMPTY);
        assertEquals("TEST", ((Map) result.get("dynamicFields")).get("ddpInstance"));
        assertEquals("VALUE", ((Map) result.get("dynamicFields")).get("ddpValue"));
        assertEquals(true, ((Map) result.get("dynamicFields")).get("booleanVal"));
        assertEquals(5L, ((Map) result.get("dynamicFields")).get("longVal"));
    }

    @Test
    public void transformArrayJsonToMap() {

        setUp();

        String json = "[{\"isCorrected\": true, \"result\": \"Negative\", \"timeCompleted\": \"2020-09-03T12:08:21.657Z\"}]";

        Map<String, Object> result = new ObjectTransformer()
                .convertToMap("test_result", json, StringUtils.EMPTY);

        Map<String, Object> testResult = (Map) ((List) result.get("testResult")).get(0);
        assertTrue((boolean) testResult.get("isCorrected"));
        assertEquals("Negative", testResult.get("result"));
        assertEquals("2020-09-03T12:08:21.657Z", testResult.get("timeCompleted"));
    }

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

        Class<?> clazz = null;
        try {
            clazz = Util.getParameterizedType(listField.getGenericType());
            assertEquals(Object.class, clazz);
            clazz = Util.getParameterizedType(followUps.getGenericType());
            assertEquals(FollowUp.class, clazz);
            clazz = Util.getParameterizedType(obj.getGenericType());
            assertEquals(Object.class, clazz);
        } catch (ClassNotFoundException e) {
            Assert.fail();
        }
    }

    @Test
    public void camelCaseToPascalSnakeCase() {
        String camelCase1 = "registrationType";
        String camelCase2 = "test";
        String camelCase3 = "medicalRecordsReleaseObtained";

        String pascalSnakeCase1 = Util.camelCaseToPascalSnakeCase(camelCase1);
        String pascalSnakeCase2 = Util.camelCaseToPascalSnakeCase(camelCase2);
        String pascalSnakeCase3 = Util.camelCaseToPascalSnakeCase(camelCase3);

        assertEquals("REGISTRATION_TYPE", pascalSnakeCase1);
        assertEquals("TEST", pascalSnakeCase2);
        assertEquals("MEDICAL_RECORDS_RELEASE_OBTAINED", pascalSnakeCase3);
    }

    @Test
    public void transformObjectCollectionToCollectionMap() {
        CohortTag cohortTag = new CohortTag();
        cohortTag.setCohortTagName("TestTag");
        cohortTag.setCohortTagId(999);
        cohortTag.setDdpInstanceId(999);
        cohortTag.setDdpParticipantId("TESTGUID");

        CohortTag cohortTag2 = new CohortTag();
        cohortTag2.setCohortTagName("TestTag2");
        cohortTag2.setCohortTagId(9999);
        cohortTag2.setDdpInstanceId(9999);
        cohortTag2.setDdpParticipantId("TESTGUID2");

        List<CohortTag> cohortTags = Arrays.asList(cohortTag, cohortTag2);

        List<Map<String, Object>> actualCohortTagsAsMaps = new ObjectTransformer()
                .transformObjectCollectionToCollectionMap((List) cohortTags, "");

        Map<String, Object> expectedCohortTagAsMap = Map.of(
                "cohortTagName", "TestTag",
                "cohortTagId", 999,
                "ddpInstanceId", 999,
                "ddpParticipantId", "TESTGUID"
        );

        Map<String, Object> expectedCohortTagAsMap2 = Map.of(
                "cohortTagName", "TestTag2",
                "cohortTagId", 9999,
                "ddpInstanceId", 9999,
                "ddpParticipantId", "TESTGUID2"
        );

        List<Map<String, Object>> expectedCohortTagsAsList = Arrays.asList(expectedCohortTagAsMap, expectedCohortTagAsMap2);

        assertEquals(expectedCohortTagsAsList, actualCohortTagsAsMaps);

    }

    @Test
    public void convertToMapFollowUps() {
        String fieldName = "follow_ups";
        FollowUp followUp = new FollowUp("2019-04-18", "2019-04-18", "2019-04-18", "2019-04-19");
        FollowUp followUp2 = new FollowUp("2020-04-18", "2020-04-18", "2020-04-18", "2020-04-19");
        List<FollowUp> followUps = Arrays.asList(followUp, followUp2);

        Map<String, Object> actualFollowUps = new ObjectTransformer().convertToMap(fieldName, followUps, StringUtils.EMPTY);

        String followUpsJson =
                 "[{\"fRequest1\":\"2019-04-18\",\"fRequest2\":\"2019-04-18\",\"fRequest3\":\"2019-04-18\",\"fReceived\":\"2019-04-19\"},"
                + "{\"fRequest1\":\"2020-04-18\",\"fRequest2\":\"2020-04-18\",\"fRequest3\":\"2020-04-18\",\"fReceived\":\"2020-04-19\"}]";

        Map<String, String> expectedFollowUps = Map.of("followUps", followUpsJson);

        assertEquals(expectedFollowUps, actualFollowUps);
    }

    @Test
    public void convertToMapTestResult() {
        String fieldName = "test_result";
        String testResult = "[{\"isCorrected\":false,\"result\":\"UNSATISFACTORY_12\",\"timeCompleted\":\"2020-09-03T12:08:21.657Z\"}]";

        Map<String, Object> actualTestResult = new ObjectTransformer().convertToMap(fieldName, testResult, StringUtils.EMPTY);

        Map<String, Object> testResultAsMap = Map.of(
                "isCorrected", false,
                "result", "UNSATISFACTORY_12",
                "timeCompleted", "2020-09-03T12:08:21.657Z"
        );

        Map<String, List<Map<String, Object>>> expectedTestResults = Map.of("testResult", List.of(testResultAsMap));

        assertEquals(expectedTestResults, actualTestResult);
    }

    @Test
    public void convertToMapEmptyTestResult() {
        String fieldName = "test_result";
        String testResult = "";

        Map<String, Object> actualTestResult = new ObjectTransformer().convertToMap(fieldName, testResult, StringUtils.EMPTY);

        assertEquals(Map.of("testResult", List.of(Map.of())), actualTestResult);
    }

    @Test
    public void convertToMapDefaultValue() {
        String fieldName = "random_field";
        Boolean boolValue = true;

        Map<String, Object> actualMap = new ObjectTransformer().convertToMap(fieldName, boolValue, StringUtils.EMPTY);

        Map<String, Boolean> expectedMap = Map.of("randomField", true);

        assertEquals(expectedMap, actualMap);
    }

    @Test
    public void convertToMapNA() {
        String fieldName = "random_date_field";
        String value = "N/A";

        Map<String, Object> actualMap = new ObjectTransformer().convertToMap(fieldName, value, StringUtils.EMPTY);

        Map<String, String> expectedMap = Map.of("randomDateField", ValueParser.N_A_SYMBOLIC_DATE);

        assertEquals(expectedMap, actualMap);

    }

}
