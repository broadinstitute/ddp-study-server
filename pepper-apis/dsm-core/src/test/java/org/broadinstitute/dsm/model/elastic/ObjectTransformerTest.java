package org.broadinstitute.dsm.model.elastic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Before;
import org.junit.Test;

public class ObjectTransformerTest {

    @Before
    public void setUp() {
        FieldSettingsDao.setInstance(UtilTest.getMockFieldSettingsDao());
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
        ObjectTransformer transformer = new ObjectTransformer(StringUtils.EMPTY);
        transformer.setParser(dynamicFieldsParser);
        Map<String, Object> result = transformer.transformObjectToMap(participantData);
        assertEquals("TEST", ((Map) result.get("dynamicFields")).get("ddpInstance"));
        assertEquals("VALUE", ((Map) result.get("dynamicFields")).get("ddpValue"));
        assertEquals(true, ((Map) result.get("dynamicFields")).get("booleanVal"));
        assertEquals(5L, ((Map) result.get("dynamicFields")).get("longVal"));
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

        List<Map<String, Object>> actualCohortTagsAsMaps = new ObjectTransformer(StringUtils.EMPTY)
                .transformObjectCollectionToCollectionMap((List) cohortTags);

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
    public void transformObjectToMap() {
        Participant participant = new Participant(
                1L, "QWERTY", 1, null,
                null, "instance", "2020-10-28",
                "2020-10-28", "2020-10-28", "2020-10-28",
                "ptNotes", true, true,
                "additionalValuesJson", 1934283746283L);
        Map<String, Object> transformedObject = new ObjectTransformer(StringUtils.EMPTY)
                .transformObjectToMap(participant);
        assertEquals(1L, transformedObject.get("participantId"));
        assertEquals("QWERTY", transformedObject.get("ddpParticipantId"));
        assertEquals("2020-10-28", transformedObject.get("created"));
        assertEquals(true, transformedObject.get("minimalMr"));
        assertEquals(1934283746283L, transformedObject.get("exitDate"));
    }

    @Test
    public void getDeclaredFieldsIncludingSuperClasses() throws NoSuchFieldException {

        class SuperSuperClass {
            private String superSuperField;
        }

        class SuperClass extends SuperSuperClass {
            private String superField;
        }

        class SimpleClass extends SuperClass {
            private String simpleField;
        }

        ObjectTransformer objectTransformer = new ObjectTransformer(StringUtils.EMPTY);

        List<Field> actualFields = objectTransformer.getDeclaredFieldsIncludingSuperClasses(SimpleClass.class);

        Field superSuperField = SuperSuperClass.class.getDeclaredField("superSuperField");
        Field superField = SuperClass.class.getDeclaredField("superField");
        Field simpleField = SimpleClass.class.getDeclaredField("simpleField");

        List<Field> expectedFields = Arrays.asList(superSuperField, superField, simpleField);

        assertTrue(actualFields.containsAll(expectedFields));
    }
}
