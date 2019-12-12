package org.broadinstitute.ddp.export.json.structured;

import java.time.LocalDate;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;

public class DsmComputedRecord {

    @SerializedName("dateOfBirth")
    private String dateOfBirth;
    @SerializedName("diagnosisYear")
    private Integer diagnosisYear;
    @SerializedName("diagnosisMonth")
    private Integer diagnosisMonth;
    @SerializedName("hasConsentedToBloodDraw")
    private boolean hasConsentedToBloodDraw;
    @SerializedName("hasConsentedToTissueSample")
    private boolean hasConsentedToTissueSample;

    private transient DateValue birthDate;
    private transient DateValue diagnosisDate;

    public DsmComputedRecord(DateValue birthDate, DateValue diagnosisDate,
                             boolean hasConsentedToBloodDraw, boolean hasConsentedToTissueSample) {
        this.birthDate = birthDate;
        this.diagnosisDate = diagnosisDate;
        this.hasConsentedToBloodDraw = hasConsentedToBloodDraw;
        this.hasConsentedToTissueSample = hasConsentedToTissueSample;

        if (birthDate != null) {
            this.dateOfBirth = birthDate.asLocalDate().map(LocalDate::toString).orElse(null);
        }

        if (diagnosisDate != null) {
            if (diagnosisDate.getYear() != null) {
                this.diagnosisYear = diagnosisDate.getYear();
            }
            if (diagnosisDate.getMonth() != null) {
                this.diagnosisMonth = diagnosisDate.getMonth();
            }
        }
    }
}
