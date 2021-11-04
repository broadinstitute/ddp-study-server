package org.broadinstitute.lddp.email;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.util.CheckValidity;
import org.broadinstitute.lddp.user.BasicUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ebaker on 5/27/16.
 */
@Data
public class Recipient implements BasicUser, CheckValidity
{
    private String firstName = null;
    private String lastName = null;
    private String email = null;
    private String currentStatus = null;
    private String url = null;
    private String pdfUrl = null;
    private String id = null;
    private String language = null;
    private String completedSurveySessionId = null;
    private String completedSurvey = null;
    private int shortId = 0;
    private String dateExited = null;
    private String adminRecipientEmail = "";
    private Map<String, String> surveyLinks = new HashMap<>();

    public Recipient()
    {

    }

    public Recipient(@NonNull String email)
    {
        this.email = email;
    }

    public Recipient(@NonNull String firstName, @NonNull String lastName, @NonNull String email)
    {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public Recipient(@NonNull String firstName, @NonNull String lastName, @NonNull String email, @NonNull String id)
    {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.id = id;
    }

    public Recipient(@NonNull String firstName, @NonNull String lastName, @NonNull String email, @NonNull String currentStatus,
                     String url, int shortId, String dateExited)
    {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.currentStatus = currentStatus;
        this.url = url;
        this.shortId = shortId;
        this.dateExited = dateExited;
    }

    public boolean isValid()
    {
        return (!email.isEmpty());
    }

    public String emailClientToAddress()
    {
        return (StringUtils.isBlank(getAdminRecipientEmail())) ? getEmail() : getAdminRecipientEmail();
    }
}

