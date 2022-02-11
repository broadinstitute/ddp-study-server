package org.broadinstitute.dsm.model.elastic;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Getter
@Setter
public class ESDsm {

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

    @SerializedName(ESObjectConstants.PARTICIPANT_DATA)
    List<ParticipantData> participantData;

    @SerializedName(ESObjectConstants.PARTICIPANT)
    Participant participant;

    List<KitRequestShipping> kitRequestShipping;

    OncHistory oncHistory;

    List<Map<String, Object>> medicalRecords;

    List<Map<String, Object>> tissueRecords;

    public List<Tissue> getTissue() {
        if (tissue == null) tissue = Collections.emptyList();
        return tissue;
    }

    public List<MedicalRecord> getMedicalRecord() {
        if (medicalRecord == null) medicalRecord = Collections.emptyList();
        return medicalRecord;
    }

    public List<OncHistoryDetail> getOncHistoryDetail() {
        if (oncHistoryDetail == null) oncHistoryDetail = Collections.emptyList();
        return oncHistoryDetail;
    }

    public List<ParticipantData> getParticipantData() {
        if (participantData == null) participantData = Collections.emptyList();
        return participantData;
    }

    public List<KitRequestShipping> getKitRequestShipping() {
        if (kitRequestShipping == null) kitRequestShipping = Collections.emptyList();
        return kitRequestShipping;
    }

    public Optional<OncHistory> getOncHistory() {
        return Optional.ofNullable(oncHistory);
    }

    public Optional<Participant> getParticipant() {
        return Optional.ofNullable(participant);
    }
}
