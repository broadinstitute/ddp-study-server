package org.broadinstitute.dsm.model.elastic;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.TestInstanceCreator;
import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.Filter;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ObjectTransformerIntegrationTest {

    public static final String TEST_FIELD_TYPE = "TestFieldType";
    public static final String TEST_COLUMN = "TEST_COLUMN";
    static Integer newlyCreatedFieldSettingsId;
    static FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();

    private static final TestInstanceCreator testInstanceCreator = new TestInstanceCreator();

    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
        FieldSettingsDto fieldSettingsDto = new FieldSettingsDto.Builder(testInstanceCreator.create())
                .withColumnName(TEST_COLUMN)
                .withDisplayType(Filter.TEXT)
                .withFieldType(TEST_FIELD_TYPE)
                .build();
        newlyCreatedFieldSettingsId = fieldSettingsDao.create(fieldSettingsDto);
    }

    @AfterClass
    public static void finish() {
        if (Objects.nonNull(newlyCreatedFieldSettingsId)) {
            fieldSettingsDao.delete(newlyCreatedFieldSettingsId);
        }
        testInstanceCreator.delete();
    }


    @Test
    public void transformObjectToMap() {
        ObjectTransformer objectTransformer = new ObjectTransformer(TestInstanceCreator.TEST_INSTANCE);
        String json = "{\"TEST_COLUMN\":\"TestValue\"}";
        long participantDataId = 9999;
        ParticipantData participantData = new ParticipantData(participantDataId, TEST_FIELD_TYPE, json);
        Map<String, Object> actualObjectMap =
                objectTransformer.transformObjectToMap(participantData);

        Map<String, String> dynamicFields = Map.of(
                "testColumn", "TestValue"
        );
        Map<String, Object> expectedObjectMap = Map.of(
                "participantDataId", participantDataId,
                "fieldTypeId", TEST_FIELD_TYPE,
                "dynamicFields", new HashMap<>(dynamicFields),
                "ddpParticipantId", 0L
        );


        Assert.assertEquals(participantDataId, actualObjectMap.get("participantDataId"));
        Assert.assertEquals(TEST_FIELD_TYPE, actualObjectMap.get("fieldTypeId"));
        Assert.assertEquals("TestValue", ((Map)actualObjectMap.get("dynamicFields")).get("testColumn"));

    }

}
