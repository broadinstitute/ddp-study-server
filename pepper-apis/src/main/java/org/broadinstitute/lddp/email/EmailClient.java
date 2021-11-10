package org.broadinstitute.lddp.email;

import com.google.gson.JsonObject;

public interface EmailClient {
    public void configure(String emailClientKey, JsonObject settings, String frontendUrl, String environment);
    public String sendSingleEmail(String template, Recipient recipient, String customAttachmentClassNames);
    public void sendSingleNonTemplate(String emailAddress, String subject, String message, String messageType);
}