package org.broadinstitute.dsm.model.elastic;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Dsm {

    @SerializedName(ESObjectConstants.DATE_OF_MAJORITY)
    Object dateOfMajority;

    @SerializedName(ESObjectConstants.FAMILY_ID)
    String familyId;

    @SerializedName(ESObjectConstants.HAS_CONSENTED_TO_BLOODD_RAW)
    boolean hasConsentedToBloodDraw;

    @SerializedName(ESObjectConstants.PDFS)
    List pdfs;

    @SerializedName(ESObjectConstants.DATE_OF_BIRTH)
    String dateOfBirth;

    @SerializedName(ESObjectConstants.DIAGNOSIS_MONTH)
    Object diagnosisMonth;

    @SerializedName(ESObjectConstants.HAS_CONSENTED_TO_TISSUE_SAMPLE)
    boolean hasConsentedToTissueSample;

    @SerializedName(ESObjectConstants.DIAGNOSIS_YEAR)
    Object diagnosisYear;

    @SerializedName(ESObjectConstants.TISSUE)
    List<Tissue> tissue;

    @SerializedName(ESObjectConstants.MEDICAL_RECORD)
    List<MedicalRecord> medicalRecord;

    @SerializedName(ESObjectConstants.ONC_HISTORY_DETAIL_RECORDS)
    List<OncHistoryDetail> oncHistoryDetail;

    @SerializedName(ESObjectConstants.SOMATIC_RESULT_UPLOAD)
    List<SomaticResultUpload> somaticResultUpload;

    @SerializedName(ESObjectConstants.PARTICIPANT_DATA)
    List<ParticipantData> participantData;

    @SerializedName(ESObjectConstants.PARTICIPANT)
    Participant participant;

    @SerializedName(ESObjectConstants.NEW_OSTEO_PARTICIPANT)
    Participant newOsteoParticipant;

    List<KitRequestShipping> kitRequestShipping;

    OncHistory oncHistory;

    List<Map<String, Object>> medicalRecords;

    List<Map<String, Object>> tissueRecords;

    List<SmId> smId;

    List<CohortTag> cohortTag;

    List<ClinicalOrder> clinicalOrder;

    public List<Tissue> getTissue() {
        if (tissue == null) {
            tissue = Collections.emptyList();
        }
        return tissue;
    }

    public List<SmId> getSmId() {
        if (smId == null) {
            smId = Collections.emptyList();
        }
        return smId;
    }

    public List<MedicalRecord> getMedicalRecord() {
        if (medicalRecord == null) {
            medicalRecord = Collections.emptyList();
        }
        return medicalRecord;
    }

    public List<OncHistoryDetail> getOncHistoryDetail() {
        if (oncHistoryDetail == null) {
            oncHistoryDetail = Collections.emptyList();
        }
        return oncHistoryDetail;
    }

    public List<SomaticResultUpload> getSomaticResultUploads() {
        if (somaticResultUpload == null) {
            somaticResultUpload = Collections.emptyList();
        }
        return somaticResultUpload;
    }

    public List<ParticipantData> getParticipantData() {
        if (participantData == null) {
            participantData = Collections.emptyList();
        }
        return participantData;
    }

    public List<KitRequestShipping> getKitRequestShipping() {
        if (kitRequestShipping == null) {
            kitRequestShipping = Collections.emptyList();
        }
        return kitRequestShipping;
    }

    public Optional<OncHistory> getOncHistory() {
        return Optional.ofNullable(oncHistory);
    }

    @JsonGetter("oncHistory")
    private OncHistory oncHistory() {
        return oncHistory;
    }

    public Optional<Participant> getParticipant() {
        return Optional.ofNullable(participant);
    }

    @JsonGetter("participant")
    public Participant participant() {
        return participant;
    }

    public List<CohortTag> getCohortTag() {
        if (cohortTag == null) {
            cohortTag = Collections.emptyList();
        }
        return cohortTag;
    }

    public Optional<Participant> getNewOsteoParticipant() {
        return Optional.ofNullable(newOsteoParticipant);
    }

    @JsonGetter("newOsteoParticipant")
    public Participant newOsteoParticipant() {
        return newOsteoParticipant;
    }

    public List<ClinicalOrder> getClinicalOrder() {
        if (clinicalOrder == null) {
            clinicalOrder = Collections.emptyList();
        }
        return clinicalOrder;
    }
}
