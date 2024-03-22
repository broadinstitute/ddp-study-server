package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import lombok.Getter;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;

public class InstitutionListRequest {
    @Getter
    private List<InstitutionRequest> institutionRequests;

    // for JSON deserialization
    public InstitutionListRequest() {}

    public InstitutionListRequest(List<InstitutionRequest> institutionRequests) {
        this.institutionRequests = institutionRequests;
    }

    public static InstitutionListRequest fromJson(String payload) {
        InstitutionListRequest req;
        try {
            req = ObjectMapperSingleton.instance().readValue(payload, InstitutionListRequest.class);
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid institution list request format. Payload: " + payload, e);
        }
        if (req.institutionRequests == null || req.institutionRequests.isEmpty()) {
            throw new DSMBadRequestException("Invalid institution list request. Empty institution list");
        }
        return req;
    }
}
