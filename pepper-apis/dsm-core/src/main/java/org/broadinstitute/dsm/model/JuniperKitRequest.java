package org.broadinstitute.dsm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class JuniperKitRequest {
    //This class is to imagine what the request payload from Juniper will/should look like
    private String firstName;
    private String lastName;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String phoneNumber;
    String juniperKitId;
    String juniperParticipantID;
    String juniperStudyID;
}
