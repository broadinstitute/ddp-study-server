package org.broadinstitute.dsm.model;

import lombok.Data;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class NDIUploadObject {

    private static final Logger logger = LoggerFactory.getLogger(NDIUploadObject.class);

    private String firstName;
    private String lastName;
    private String middle;
    private String year;
    private String month;
    private String day;
    private String ddpParticipantId;


    public NDIUploadObject(@NonNull String firstName, @NonNull String lastName, @NonNull String middle, @NonNull String year, @NonNull String month, @NonNull String day,
                           @NonNull String ddpParticipantId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.middle = middle;
        this.year = year;
        this.month = month;
        this.day = day;
        this.ddpParticipantId = ddpParticipantId;
    }
}
