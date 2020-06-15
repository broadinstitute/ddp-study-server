package org.broadinstitute.ddp.constants;

/**
 * Magic global vars that can be dropped into a sendgrid
 * template and will be automatically replaced by email
 * templating system.
 */
public class NotificationTemplateVariables {

    /**
     * First name of the participant
     */
    public static final String DDP_PARTICIPANT_FIRST_NAME = "-ddp.participant.firstName-";

    /**
     * Last name of the participant
     */
    public static final String DDP_PARTICIPANT_LAST_NAME = "-ddp.participant.lastName-";

    /**
     * Guid of the participant
     */
    public static final String DDP_PARTICIPANT_GUID = "-ddp.participant.guid-";

    /**
     * Hruid of the participant
     */
    public static final String DDP_PARTICIPANT_HRUID = "-ddp.participant.hruid-";

    /**
     * Notes about the participant's exit request
     */
    public static final String DDP_PARTICIPANT_EXIT_NOTES = "-ddp.participant.exitNotes-";

    /**
     * First name of the proxy who is receiving the email notification on behalf of the participant
     */
    public static final String DDP_PROXY_FIRST_NAME = "-ddp.proxy.firstName-";

    /**
     * Last name of the proxy who is receiving the email notification on behalf of the participant
     */
    public static final String DDP_PROXY_LAST_NAME = "-ddp.proxy.lastName-";

    /**
     * An activity instance guid
     */
    public static final String DDP_ACTIVITY_INSTANCE_GUID = "-ddp.activityInstanceGuid-";

    /**
     * Email of the participant
     */
    public static final String DDP_PARTICIPANT_FROM_EMAIL = "-ddp.participant.fromEmail-";

    /**
     * The actual greeting used at the beginning of emails
     * (either a default salutation OR "Dear " + the user's first and last name)
     */
    public static final String DDP_SALUTATION = "-ddp.salutation-";

    /**
     * The base web url
     */
    public static final String DDP_BASE_WEB_URL = "-ddp.baseWebUrl-";

    /**
     * A URL value to be included in email
     */
    public static final String DDP_LINK = "-ddp.link-";

    /**
     * The study guid
     */
    public static final String DDP_STUDY_GUID = "-ddp.study.guid-";

    /**
     * The invitation guid
     */
    public static final String DDP_INVITATION_ID = "-ddp.invitationId-";

    //Dynamic template variables
    public static final String PARTICIPANT_FIRST_NAME = "participant_firstName";
    public static final String PARTICIPANT_LAST_NAME = "participant_lastName";
    public static final String PARTICIPANT_GUID = "participant_guid";
    public static final String PARTICIPANT_HRUID = "participant_hruid";
    public static final String PARTICIPANT_EXIT_NOTES = "participant_exitNotes";
    public static final String SALUTATION = "salutation";
    public static final String PROXY_FIRST_NAME = "proxy_firstName";
    public static final String PROXY_LAST_NAME = "proxy_lastName";
    public static final String ACTIVITY_INSTANCE_GUID = "activityInstance_guid";
    public static final String PARTICIPANT_FROM_EMAIL = "participant_fromEmail";
    public static final String BASE_WEB_URL = "baseWebUrl";
    public static final String LINK = "link";
    public static final String STUDY_GUID = "study_guid";
    public static final String INVITATION_ID = "invitationId";

}
