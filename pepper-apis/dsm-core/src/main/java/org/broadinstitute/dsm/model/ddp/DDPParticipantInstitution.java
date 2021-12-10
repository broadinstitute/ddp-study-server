package org.broadinstitute.dsm.model.ddp;

import lombok.Data;

@Data
public class DDPParticipantInstitution {

    private String firstName;
    private String lastName;
    private String shortId;
    private String legacyShortId;
    private String physician;
    private String institution;
    private String streetAddress;
    private String city;
    private String state;

    public DDPParticipantInstitution(String firstName, String lastName, String shortId, String legacyShortId, String physician, String institution,
                                     String streetAddress, String city, String state) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.shortId = shortId;
        this.legacyShortId = legacyShortId;
        this.physician = physician;
        this.institution = institution;
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
    }
}
