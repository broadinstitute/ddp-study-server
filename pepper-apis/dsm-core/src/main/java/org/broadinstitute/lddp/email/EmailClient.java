package org.broadinstitute.lddp.email;

import com.google.gson.JsonObject;

public interface EmailClient {
    void configure(String emailClientKey, JsonObject settings, String frontendUrl, String environment);

    String sendSingleEmail(String template, Recipient recipient, String customAttachmentClassNames);

    void sendSingleNonTemplate(String emailAddress, String subject, String message, String messageType);
}
