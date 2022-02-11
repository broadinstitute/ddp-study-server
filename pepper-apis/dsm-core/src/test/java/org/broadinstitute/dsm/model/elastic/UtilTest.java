package org.broadinstitute.dsm.model.elastic;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.FollowUp;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UtilTest {

    @Before
    public void setUp() {
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

        FieldSettingsDaoMock fieldSettingsDaoMock = new FieldSettingsDaoMock();

        FieldSettingsDao.setInstance(fieldSettingsDaoMock);
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
                1, "QWERTY", null,
                null, "instance", "2020-10-28",
                "2020-10-28", "2020-10-28", "2020-10-28",
                "ptNotes", true, true,
                "additionalValuesJson", 1934283746283L);
        Map<String, Object> transformedObject = Util.transformObjectToMap(participant, "angio");
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

        Map<String, Object> result = Util.transformObjectToMap(participantData, "angio");
        assertEquals("TEST", ((Map) result.get("dynamicFields")).get("ddpInstance"));
        assertEquals("VALUE", ((Map) result.get("dynamicFields")).get("ddpValue"));
        assertEquals(true, ((Map) result.get("dynamicFields")).get("booleanVal"));
        assertEquals(5L, ((Map) result.get("dynamicFields")).get("longVal"));
    }

    @Test
    public void transformArrayJsonToMap() {

        setUp();

        String json = "[{\"isCorrected\": true, \"result\": \"Negative\", \"timeCompleted\": \"2020-09-03T12:08:21.657Z\"}]";

        Map<String, Object> result = Util.convertToMap("test_result", json, StringUtils.EMPTY);

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

}