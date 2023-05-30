package org.broadinstitute.dsm.model.nonpepperkit;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.model.KitRequest;

@AllArgsConstructor
@Data
public class JuniperKitRequest extends KitRequest {
    boolean skipAddressValidation;
    private String firstName;
    private String lastName;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String phoneNumber;
    private String juniperKitId;
    private String juniperParticipantID;
    private String juniperStudyID;
    private String easypostAddressId;
}
