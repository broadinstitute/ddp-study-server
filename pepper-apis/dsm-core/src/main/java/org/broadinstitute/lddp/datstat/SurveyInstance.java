package org.broadinstitute.lddp.datstat;

import org.broadinstitute.lddp.email.Recipient;

import java.util.HashMap;

public interface SurveyInstance {

    //Whether or not missing UUIDs should be generated for JSON fields.
    void setJsonUUIDNeeded(boolean jsonUUIDNeeded);

    boolean isJsonUUIDNeeded();

    //Whether or not an account is needed to access the survey
    void setAccountNeeded(boolean accountNeeded);

    boolean isAccountNeeded();

    /**
     * Sets DDP_LASTUPDATED to current UTC datetime.
     */
    void setSurveyLastUpdated();

    /**
     * Sets DDP_FIRSTCOMPLETED to current UTC datetime.
     */
    void setSurveyFirstCompleted();

    String getSurveyLastUpdated();
    String getSurveyCreated();
    String getSurveyFirstCompleted();

    void initialize(Recipient recipient, String followUpInstanceId);

    /**
     * @return the underlying datstat survey session dto
     */
    SurveySession getSurveySession();

    void setSurveySession(SurveySession session);

    /**
     * @return the datstat altpid for this survey
     */
    String getAltPid();

    void setAltPid(String altPid);

    /**
     * @return the participant shortId for this survey
     */
    String getParticipantShortId();

    void setParticipantShortId(String participantShortId);

    /**
     * Callback that surveys can use to rejigger internal
     * state for complex json objects.  This method is called
     * by SurveyService when all datstat fields have been set
     * on the object.
     */
    public void onDatstatLoadComplete();

    public SubmissionStatus getSubmissionStatus();

    /**
     * Updates the value of the field whose {@link UIAlias} is
     * the given uiFieldName.  For more complex fields (things other than
     * DateTime, String, Boolean, and Numbers), subclasses should
     * override this method on an as-needed basis (and probably
     * call super.applyChange() first).
     * @param uiFieldName name of the {@link UIAlias} annotated field
     * @param newValue new value to assign to the given field
     */
    public void applyChange(String uiFieldName,Object newValue);

    public void applyChangeMap(HashMap<String, Object> changes);

    /**
     * Sets the submission status
     */
    public void setSubmissionStatus(SubmissionStatus status);

    public void updateParticipantInfo( Recipient recipient, DatStatUtil datStatUtil);

    public static enum SubmissionStatus {

        COMPLETE(1), STARTED(2), TERMINATED(5);

        private final int datstatValue;

        SubmissionStatus(int datstatValue) {
            this.datstatValue = datstatValue;
        }

        public int getDatstatValue() {
            return datstatValue;
        }
    }

    public void setSurveyReadOnlyStatus();

    public void runCreationPostProcessing(Recipient recipient, String sessionId, DatStatUtil datStatUtil);

    public void runCompletionPostProcessing(Recipient recipient, String nextSessionId, DatStatUtil datStatUtil, boolean alreadyCompleted, boolean anonymous, String currentSurvey);

    public Recipient getRecipient();
}
