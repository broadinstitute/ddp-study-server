package org.broadinstitute.ddp.email;

import com.google.gson.JsonObject;
import org.broadinstitute.ddp.util.EDCClient;

import java.util.Collection;

public interface EmailClient {
    public void configure(String emailClientKey, JsonObject settings, String frontendUrl, EDCClient edc, String environment);
    public String sendSingleEmail(String template, Recipient recipient, String customAttachmentClassNames);
    public void sendSingleNonTemplate(String emailAddress, String subject, String message, String messageType);
}