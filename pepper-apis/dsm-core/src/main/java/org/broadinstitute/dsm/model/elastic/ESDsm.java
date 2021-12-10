package org.broadinstitute.dsm.model.elastic;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Getter
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

    @SerializedName(ESObjectConstants.TISSUE_RECORDS)
    List<Map<String, Object>> tissueRecords;

    @SerializedName(ESObjectConstants.MEDICAL_RECORDS)
    List<Map<String, Object>> medicalRecords;

    @SerializedName(ESObjectConstants.ONC_HISTORY_DETAIL_RECORDS)
    List<Map<String, Object>> oncHistoryDetailRecords;

    @SerializedName(ESObjectConstants.ONC_HISTORY)
    Map<String, Object> oncHistory;

    @SerializedName(ESObjectConstants.PARTICIPANT_DATA)
    List<Map<String, Object>> participantData;

    @SerializedName(ESObjectConstants.PARTICIPANT_RECORD)
    Map<String, Object> participantRecord;

    @SerializedName(ESObjectConstants.PARTICIPANT)
    Map<String, Object> participant;


}
