package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import com.google.gson.Gson;
import lombok.Data;
import org.broadinstitute.dsm.exception.DSMBadRequestException;

@Data
public class ParticipantListRequest {
    private List<String> participants;

    public ParticipantListRequest(List<String> participants) {
        this.participants = participants;
    }

    public static ParticipantListRequest fromJson(String payload) {
        ParticipantListRequest req;
        try {
            req = new Gson().fromJson(payload, ParticipantListRequest.class);
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid participant list request format. Payload: " + payload);
        }
        if (req.participants == null || req.participants.isEmpty()) {
            throw new DSMBadRequestException("Invalid participant list request. Empty participant list");
        }
        return req;
    }
}
