package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * Handling of participant list request body for an AdminOperation
 */
@Data
public class ParticipantListRequest {
    private List<String> participants;

    // for JSON deserialization
    public ParticipantListRequest() {}

    public ParticipantListRequest(List<String> participants) {
        this.participants = participants;
    }

    public static ParticipantListRequest fromJson(String payload) {
        ParticipantListRequest req;
        try {
            req = ObjectMapperSingleton.instance().readValue(payload, ParticipantListRequest.class);
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid participant list request format. Payload: " + payload, e);
        }
        if (req.participants == null || req.participants.isEmpty()) {
            throw new DSMBadRequestException("Invalid participant list request. Empty participant list");
        }
        return req;
    }
}
