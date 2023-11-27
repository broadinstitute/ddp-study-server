package org.broadinstitute.lddp.email;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.lddp.user.BasicUser;

@Data
public class Recipient implements BasicUser {
    private String firstName = null;
    private String lastName = null;
    private String email = null;
    private int shortId = 0;
    private String permalink = null;
    private String currentStatus = null;
    private Map<String, String> personalization = new HashMap<>();


    public Recipient() {
    }

    public Recipient(@NonNull String email) {
        this.email = email;
    }

}
