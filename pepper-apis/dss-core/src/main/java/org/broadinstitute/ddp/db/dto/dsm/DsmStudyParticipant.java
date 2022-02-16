package org.broadinstitute.ddp.db.dto.dsm;

import lombok.Data;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

/**
 * POJO for study participant info. Created specifically to support DSM integration JSON API.
 */
@Data
public class DsmStudyParticipant {
    private String participantId;
    private String firstName;
    private String lastName;
    private String mailToName;
    private String country;
    private String shortId;
    private String legacyShortId;

    /**
     * Report the validation status of the mailing address.
     * “0” for an address that EasyPost says is invalid.
     * “1” for an address that EasyPost says is valid and contains suggested EasyPost corrections.
     * “2” for an address that EasyPost says is valid but does not contain EasyPost suggested corrections.
     */
    private String validAddress;

    private String city;
    private String postalCode;
    private String street1;
    private String street2;
    private String state;
    private String userGuid;
    private EnrollmentStatusType enrollmentStatusType;
    private transient long userId;
}
