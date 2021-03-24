package org.broadinstitute.ddp.handlers.util;

import lombok.Data;
import org.broadinstitute.ddp.util.DeliveryAddress;

@Data
public class ParticipantInstitutionInfo extends ParticipantInstitutionList
{
    public ParticipantInstitutionInfo() {
    }

    private String firstName;
    private String lastName;
    private String shortId;
    private String legacyShortId;
    private String surveyCreated;
    private String surveyLastUpdated;
    private String surveyFirstCompleted;
    private int addressValid;
    private DeliveryAddress address;
}