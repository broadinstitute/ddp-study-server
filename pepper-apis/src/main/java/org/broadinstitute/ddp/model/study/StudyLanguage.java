package org.broadinstitute.ddp.model.study;

public class StudyLanguage {
    private String languageCode;
    private String displayName;
    private boolean isDefault;
    private long studyId;
    private long languageId;


    public StudyLanguage(String languageCode, String displayName, boolean isDefault, long studyId, long languageId) {
        this.languageCode = languageCode;
        this.displayName = displayName;
        this.isDefault = isDefault;
        this.studyId = studyId;
        this.languageId = languageId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean getIsDefault() {
        return isDefault;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getLanguageId() {
        return languageId;
    }
}
