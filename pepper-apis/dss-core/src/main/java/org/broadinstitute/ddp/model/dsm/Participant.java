package org.broadinstitute.ddp.model.dsm;

import java.util.List;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

public class Participant {
    @SerializedName("dob")
    private String dateOfBirth;

    @SerializedName("dateOfDiagnosis")
    private String dateOfDiagnosis;

    @SerializedName("drawBloodConsent")
    private int hasConsentedToBloodDraw;

    @SerializedName("tissueSampleConsent")
    private int hasConsentedToTissueSample;

    @NotNull
    @SerializedName("participantId")
    private String participantGUID;

    @SerializedName("institutions")
    private List<Institution> institutionList;

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getDateOfDiagnosis() {
        return dateOfDiagnosis;
    }

    public void setDateOfDiagnosis(String dateOfDiagnosis) {
        this.dateOfDiagnosis = dateOfDiagnosis;
    }

    public int hasConsentedToBloodDraw() {
        return hasConsentedToBloodDraw;
    }

    public void setHasConsentedToBloodDraw(int hasConsentedToBloodDraw) {
        this.hasConsentedToBloodDraw = hasConsentedToBloodDraw;
    }

    public int hasConsentedToTissueSample() {
        return hasConsentedToTissueSample;
    }

    public void setHasConsentedToTissueSample(int hasConsentedToTissueSample) {
        this.hasConsentedToTissueSample = hasConsentedToTissueSample;
    }

    public String getParticipantGUID() {
        return participantGUID;
    }

    public void setParticipantGUID(String participantGUID) {
        this.participantGUID = participantGUID;
    }

    public List<Institution> getInstitutionList() {
        return institutionList;
    }

    public void setInstitutionList(List<Institution> institutionList) {
        this.institutionList = institutionList;
    }
}
