package org.broadinstitute.dsm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.Assert;

public class ParticipantDataTestUtil {
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();

    public static ParticipantData createParticipantData(String ddpParticipantId, Map<String, String> dataMap,
                                                        String fieldTypeId, int dppInstanceId, String userEmail) {
        ParticipantData participantData = new ParticipantData.Builder()
                .withDdpParticipantId(ddpParticipantId)
                .withDdpInstanceId(dppInstanceId)
                .withFieldTypeId(fieldTypeId)
                .withData(ObjectMapperSingleton.writeValueAsString(dataMap, false))
                .withLastChanged(System.currentTimeMillis())
                .withChangedBy(userEmail).build();

        participantData.setParticipantDataId(participantDataDao.create(participantData));
        return participantData;
    }

    public static void deleteParticipantData(int participantDataId) {
        if (participantDataId >= 0) {
            participantDataDao.delete(participantDataId);
        }
    }

    public static List<Activities> getRgpActivities() {
        try {
            String json = TestUtil.readFile("activities.json");
            JsonArray jsonArray = (JsonArray) JsonParser.parseString(json);
            Assert.assertNotNull(jsonArray);

            List<Activities> activitiesList = new ArrayList<>();
            Gson gson = new Gson();
            jsonArray.forEach(a -> activitiesList.add(gson.fromJson(a.getAsJsonObject(), Activities.class)));
            return activitiesList;
        } catch (Exception e) {
            Assert.fail("Failed to read activities.json");
            return null;
        }
    }
}
