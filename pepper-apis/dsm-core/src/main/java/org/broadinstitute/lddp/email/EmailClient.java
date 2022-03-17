package org.broadinstitute.lddp.email;

import com.google.gson.JsonObject;

public interface EmailClient {
    void configure(String emailClientKey, JsonObject settings);

    void sendSingleEmail(String template, Recipient recipient);
}
