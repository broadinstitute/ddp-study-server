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

    /**
     * Carrier's return tracking id, set when the participant
     * is preparing an in-person kit in the presence
     * of study staff.
     */
    private String returnTrackingId;

    /**
     * Set to true when the kit should not be sent by mail
     * to the participant.  Set to true when preparing in-person
     * kits.
     */
    private boolean returnOnly;

    /**
     * The label on the physical kit, also known as
     * kit barcode.  Set in conjunction with {@link #returnOnly}
     * and {@link #returnTrackingId}
     */
    private String kitLabel;
}
