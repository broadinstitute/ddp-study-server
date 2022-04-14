package org.broadinstitute.dsm.model.ddp;

import lombok.Data;

@Data
public class Contact {

    private String firstName;
    private String lastName;
    private String email;
    private String info;
    private Long dateCreated;

}
