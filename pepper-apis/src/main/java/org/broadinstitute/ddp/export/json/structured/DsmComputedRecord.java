package org.broadinstitute.ddp.export.json.structured;

import java.time.LocalDate;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;

public class DsmComputedRecord {

    @SerializedName("dateOfBirth")
    private String dateOfBirth;
    @SerializedName("dateOfMajority")
    private String dateOfMajority;
    @SerializedName("diagnosisYear")
    private Integer diagnosisYear;
    @SerializedName("diagnosisMonth")
    private Integer diagnosisMonth;
    @SerializedName("hasConsentedToBloodDraw")
    private boolean hasConsentedToBloodDraw;
    @SerializedName("hasConsentedToTissueSample")
    private boolean hasConsentedToTissueSample;
    @SerializedName("pdfs")
    private List<PdfConfigRecord> pdfConfigRecords;

    private transient DateValue birthDate;
    private transient LocalDate ageOfMajorityDate;
    private transient DateValue diagnosisDate;

    public DsmComputedRecord(DateValue birthDate, LocalDate ageOfMajorityDate, DateValue diagnosisDate,
                             boolean hasConsentedToBloodDraw, boolean hasConsentedToTissueSample,
                             List<PdfConfigRecord> pdfConfigRecords) {
        this(birthDate, ageOfMajorityDate, diagnosisDate, hasConsentedToBloodDraw, hasConsentedToTissueSample);
        this.pdfConfigRecords = pdfConfigRecords;
    }

    public DsmComputedRecord(DateValue birthDate, LocalDate ageOfMajorityDate, DateValue diagnosisDate,
                             boolean hasConsentedToBloodDraw, boolean hasConsentedToTissueSample) {
        this.birthDate = birthDate;
        this.ageOfMajorityDate = ageOfMajorityDate;
        this.diagnosisDate = diagnosisDate;
        this.hasConsentedToBloodDraw = hasConsentedToBloodDraw;
        this.hasConsentedToTissueSample = hasConsentedToTissueSample;

        if (birthDate != null) {
            this.dateOfBirth = birthDate.asLocalDate().map(LocalDate::toString).orElse(null);
        }

        if (ageOfMajorityDate != null) {
            this.dateOfMajority = ageOfMajorityDate.toString();
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
