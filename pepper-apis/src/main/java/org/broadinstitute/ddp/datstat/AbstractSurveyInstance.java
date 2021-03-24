package org.broadinstitute.ddp.datstat;

import com.google.api.client.util.Data;
import com.google.api.client.util.Key;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.handlers.SurveySessionHandler;
import org.broadinstitute.ddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Common superclass for SurveyInstances.  Most of the book-keeping
 * for DatStat fields is here, as is the core "applyChange" method,
 * which should handle applying surveysession changes that come
 * in from json in the front end.
 */
public abstract class AbstractSurveyInstance implements SurveyInstance
{
    private static final Logger logger = LoggerFactory.getLogger(AbstractSurveyInstance.class);

    public static final int LIMIT_TEXT = 255;
    public static final int LIMIT_COMMENTARY = 500;
    public static final int LIMIT_MAX = 8000;
    public static final String READONLY = "READONLY";

    @Key(DatStatUtil.ALTPID_FIELD)
    @SerializedName(DatStatUtil.ALTPID_FIELD)
    // in the API, it's possible to create survey sessions without an altpid.
    // perhaps we should leverage this for "anonymous" surveys so that
    // all survey mgmt interactions are the same, just with altpid or not.
    // could enforce the altpid requirement out at the route level.
    private String altPid = Data.nullOf(String.class);

    private SurveySession surveySession;

    @Key("DATSTAT.SESSIONID")
    @SerializedName("DATSTAT.SESSIONID")
    private String datstatSessionId = Data.nullOf(String.class);

    @Key("DATSTAT.SUBMISSIONSTATUS")
    @SerializedName("DATSTAT.SUBMISSIONSTATUS")
    protected Integer datstatStatusId = SubmissionStatus.STARTED.getDatstatValue();

    @Key("DDP_CREATED")
    @SerializedName("DDP_CREATED")
    private String surveyCreated = Data.nullOf(String.class);

    @Key("DDP_LASTUPDATED")
    @SerializedName("DDP_LASTUPDATED")
    private String surveyLastUpdated = Data.nullOf(String.class);

    @Key("DDP_FIRSTCOMPLETED")
    @SerializedName("DDP_FIRSTCOMPLETED")
    private String surveyFirstCompleted = Data.nullOf(String.class);

    //Like DATSTAT_ALTPID this isn't used for anonymous surveys right now
    @Key("DDP_PARTICIPANT_SHORTID")
    @SerializedName("DDP_PARTICIPANT_SHORTID")
    private String participantShortId = Data.nullOf(String.class);

    @UIAlias("readonly")
    private boolean readonly = false;

    private boolean jsonUUIDNeeded = false;

    private boolean accountNeeded = true;

    protected AbstractSurveyInstance()
    {
        this.surveyLastUpdated = this.surveyCreated = Utility.getCurrentUTCDateTimeAsString();
    }

    @Override
    public boolean isJsonUUIDNeeded() {
        return this.jsonUUIDNeeded;
    }

    @Override
    public void setJsonUUIDNeeded(boolean jsonUUIDNeeded) {
        this.jsonUUIDNeeded = jsonUUIDNeeded;
    }

    @Override
    public boolean isAccountNeeded() {
        return this.accountNeeded;
    }

    @Override
    public void setAccountNeeded(boolean accountNeeded) {
        this.accountNeeded = accountNeeded;
    }

    @Override
    public void setSurveyLastUpdated()
    {
        this.surveyLastUpdated = Utility.getCurrentUTCDateTimeAsString();
    }

    @Override
    public void setSurveyFirstCompleted()
    {
        this.surveyFirstCompleted = Utility.getCurrentUTCDateTimeAsString();
    }

    @Override
    public String getSurveyLastUpdated()
    {
        return this.surveyLastUpdated;
    }

    @Override
    public String getSurveyCreated()
    {
        return this.surveyCreated;
    }

    @Override
    public String getSurveyFirstCompleted()
    {
        return this.surveyFirstCompleted;
    }

    @Override
    public void setSubmissionStatus(SubmissionStatus status) {
        this.datstatStatusId = status.getDatstatValue();
    }
    public SubmissionStatus getSubmissionStatus() {
        return getSubmissionStatus(datstatStatusId);
    }

    public static SubmissionStatus getSubmissionStatus(int datstatStatusId) {
        SubmissionStatus status = null;
        for (SubmissionStatus s : SubmissionStatus.values()) {
            if (s.getDatstatValue() == datstatStatusId) {
                status = s;
            }
        }
        return status;
    }

    @Override
    public String getAltPid() {
        return this.altPid;
    }

    @Override
    public void setAltPid(String altPid) {
        this.altPid = altPid;
    }

    @Override
    public String getParticipantShortId() {
        return this.participantShortId;
    }

    @Override
    public void setParticipantShortId(String participantShortId) {
        this.participantShortId = participantShortId;
    }

    @Override
    public SurveySession getSurveySession() {
        return surveySession;
    }

    @Override
    public void setSurveySession(SurveySession session) {
        this.surveySession = session;
        this.datstatSessionId = surveySession.getSessionId();
    }

    @Override
    public void applyChange(String uiFieldName, Object newValue) {
        boolean foundField = false;
        Field[] fields;
        if (this.getClass().getSuperclass() == AbstractSurveyInstance.class) {
            fields = this.getClass().getDeclaredFields();
        } else {
            fields = this.getClass().getSuperclass().getDeclaredFields();
        }

        for (Field field : fields) {
            for (UIAlias UIAlias : field.getAnnotationsByType(UIAlias.class)) {

                if (uiFieldName.equals(UIAlias.value())) {
                    foundField = true;
                    field.setAccessible(true);
                    try {
                        //turn any empty strings into nulls (DatStat should be sent nulls not empty strings)
                        if (field.getType() == String.class) {
                            if (StringUtils.isBlank((String)newValue)) {
                                newValue = null;
                            }
                            else {
                                checkStringLength(field, (String)newValue, uiFieldName);
                            }
                        }
                        field.set(this, (newValue != null) ? newValue : Data.nullOf(field.getType()));
                    } catch (Exception e) {
                        throw new RuntimeException("Could not set field " + uiFieldName + " to " + newValue + ".  Maybe your types aren't mapped properly?", e);
                    }
                }
            }
            if (foundField) break;
        }
        if (!foundField) {
            throw new RuntimeException("Could not find field with alias annotation " + uiFieldName);
        }
    }

    public void applyChangeMap(HashMap<String, Object> changes) {
        if (changes != null) {
            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                if (!entry.getKey().equals(SurveySessionHandler.PAYLOAD_PARTICIPANT)) {
                    applyChange(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public void setSurveyReadOnlyStatus(){
        this.readonly = (datstatStatusId == SubmissionStatus.TERMINATED.getDatstatValue());
    }

    @Override
    public void runCreationPostProcessing(@NonNull Recipient recipient, @NonNull String sessionId, @NonNull DatStatUtil datStatUtil)
    {

    }

    @Override
    public void runCompletionPostProcessing(@NonNull Recipient recipient, String nextSessionId, @NonNull DatStatUtil datStatUtil,
                                            boolean alreadyCompleted, boolean anonymous, @NonNull String completedSurvey)
    {
        //update participant (e.g., status)
        if ((!anonymous)&&(!(this instanceof FollowUpSurveyType))) updateParticipantInfo(recipient, datStatUtil);

        //don't send out an email if the survey was already completed
        if (!alreadyCompleted)
        {
            //queue emails
            datStatUtil.queueCurrentAndFutureEmailsForLocalSurveyUI(nextSessionId, recipient.getCurrentStatus(), recipient, completedSurvey);
        }
    }

    /**
     * This method should be overriden for surveys that want to update other participant information besides status.
     * @param recipient
     * @param datStatUtil
     */
    public void updateParticipantInfo(@NonNull Recipient recipient, @NonNull DatStatUtil datStatUtil)
    {
        //update current status
        HashMap<String, Object> changes = new HashMap<>();
        changes.put(datStatUtil.getDatStatParticipantStatusField(), recipient.getCurrentStatus());

        datStatUtil.updateParticipantDataViaAltPid(recipient.getId(), changes);
    }

    public void checkStringLength(Field field, String value, String uiFieldName) {

        int limit = LIMIT_TEXT;
        try
        {
            if (field.isAnnotationPresent(UICustomLength.class))
            {
                UICustomLength customLength = field.getAnnotation(UICustomLength.class);
                limit = customLength.value();
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Unable to access custom length annotation.", ex);
        }

        if (value.length() > limit)
        {
            throw new RuntimeException("Input of field " + uiFieldName + " is too long (length: " + value.length() + " limit: " + limit + ") (participant with altPid: " + getAltPid() + ")");
        }
    }

    public String valueToJsonString(String fieldName, Object value) {
        if (value == null)
        {
            return Data.nullOf(String.class);
        }

        String jsonString = new GsonBuilder().serializeNulls().create().toJson(value);

        //replace empty strings from frontend with nulls here
        jsonString = jsonString.replace("\"\"", "null");
        int jsonlength = jsonString.length();
        if (jsonString.length() > LIMIT_MAX) {
            throw new RuntimeException("Input of field " + fieldName + " is too long (length: " + jsonString.length() + ") (participant with altPid: " + getAltPid() + ")");
        }
        return jsonString;
    }

    @Override
    public Recipient getRecipient()
    {
        throw new RuntimeException("getRecipient() should only be used and implemented for anonymous surveys");
    }
}

