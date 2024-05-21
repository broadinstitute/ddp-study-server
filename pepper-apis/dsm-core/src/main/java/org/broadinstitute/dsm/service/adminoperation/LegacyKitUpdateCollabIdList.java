package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Data
@NoArgsConstructor
/**
 * List of requests to update ids in kits for participants with legacy short IDs
 */
public class LegacyKitUpdateCollabIdList {
    List<UpdateKitToLegacyIdsRequest> updateCollabIdRequests;

    @JsonCreator
    public LegacyKitUpdateCollabIdList(@JsonProperty("updateCollabIdRequests")
                                           List<UpdateKitToLegacyIdsRequest> updateCollabIdRequests) {
        this.updateCollabIdRequests = updateCollabIdRequests;
    }

    public static LegacyKitUpdateCollabIdList fromJson(String payload) {
        LegacyKitUpdateCollabIdList req;
        try {
            req = ObjectMapperSingleton.instance().readValue(payload, LegacyKitUpdateCollabIdList.class);
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid update collab id list request format. Payload: " + payload, e);
        }
        if (req.updateCollabIdRequests == null || req.updateCollabIdRequests.isEmpty()) {
            throw new DSMBadRequestException("Invalid update collab id list request. Empty update collab id list");
        }
        return req;
    }
}
