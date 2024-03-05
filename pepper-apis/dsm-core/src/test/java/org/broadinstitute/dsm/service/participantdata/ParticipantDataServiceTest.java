package org.broadinstitute.dsm.service.participantdata;

import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class ParticipantDataServiceTest {

    @Test
    public void handleDuplicateRecordsTest() {
        Map<String, String> dataMap1 =
                Map.of("key1", "value1", "key2", "value2", "key4", "value3");
        Map<String, String> dataMap2 =
                Map.of("key1", "value1", "key2", "value2a", "key3", "value3");

        String ddpParticipantId = "testParticipant";
        String fieldTypeId = "testFieldTypeId";
        ParticipantData participantData1 = createParticipantData(ddpParticipantId, 1, dataMap1, fieldTypeId);
        ParticipantData participantData2 = createParticipantData(ddpParticipantId, 2, dataMap2, fieldTypeId);
        ParticipantData participantData3 = createParticipantData(ddpParticipantId, 3, dataMap2, fieldTypeId);

        List<DuplicateParticipantData> duplicates = ParticipantDataService.handleDuplicateRecords(
                List.of(participantData1, participantData2, participantData3));
        Assert.assertEquals(2, duplicates.size());
        duplicates.forEach(duplicate -> {
            Assert.assertEquals(Set.of("key4"), duplicate.getBaseOnlyKeys());
            Assert.assertEquals(Set.of("key3"), duplicate.getDuplicateOnlyKeys());
            Assert.assertTrue(duplicate.getCommonKeys().containsAll(List.of("key1", "key2")));
            Map<String, Pair<String, String>> differentValues = duplicate.getDifferentValues();
            Assert.assertEquals(1, differentValues.size());
            differentValues.forEach((key, value) -> {
                Assert.assertEquals("key2", key);
                Assert.assertEquals("value2", value.getLeft());
                Assert.assertEquals("value2a", value.getRight());
            });
        });
        log.info("Duplicates: {}", ObjectMapperSingleton.writeValueAsString(duplicates));
    }

    private static ParticipantData createParticipantData(String ddpParticipantId, int participantDataId,
                                                         Map<String, String> dataMap, String fieldTypeId) {
        return new ParticipantData.Builder()
                .withParticipantDataId(participantDataId)
                .withDdpParticipantId(ddpParticipantId)
                .withFieldTypeId(fieldTypeId)
                .withData(ObjectMapperSingleton.writeValueAsString(dataMap, false))
                .build();
    }
}
