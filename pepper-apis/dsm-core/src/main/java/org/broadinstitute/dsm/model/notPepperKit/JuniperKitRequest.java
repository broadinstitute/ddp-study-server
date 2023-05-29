package org.broadinstitute.dsm.model.notPepperKit;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.model.KitRequest;

@AllArgsConstructor
@Data
public class JuniperKitRequest extends KitRequest {
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
    private String juniperKitId;
    private String juniperParticipantID;
    private String juniperStudyID;
    boolean skipAddressValidation;
    private String easypostAddressId;
}
