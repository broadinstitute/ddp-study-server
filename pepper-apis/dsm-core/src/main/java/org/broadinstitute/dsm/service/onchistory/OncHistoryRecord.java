package org.broadinstitute.dsm.service.onchistory;

import java.util.Map;

import com.google.gson.JsonObject;
import lombok.Data;

@Data
public class OncHistoryRecord {

    private final String participantTextId;
    private final Map<String, String> columns;
    private int participantId;
    private String ddpParticipantId;
    private int recordId;
    private JsonObject additionalValues;

    public OncHistoryRecord(String participantTextId, Map<String, String> columns) {
        this.participantTextId = participantTextId;
        this.columns = columns;
        this.additionalValues = new JsonObject();
    }

    public String getAdditionalValuesString() {
        if (additionalValues.entrySet().isEmpty()) {
            return null;
        }
        return additionalValues.toString();
    }
}
