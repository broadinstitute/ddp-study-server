package org.broadinstitute.dsm.db;

public class MedicalRecordLog {

    public static final String DATA_REVIEW = "DATA_REVIEW";

    private final String medicalRecordLogId;

    private final String date;

    private final String comments;

    private final String type;

    public MedicalRecordLog(String medicalRecordLogId, String date, String comments, String type) {
        this.medicalRecordLogId = medicalRecordLogId;
        this.date = date;
        this.comments = comments;
        this.type = type;
    }

    public String getDate() {
        return date;
    }

    public String getComments() {
        return comments;
    }

    public String getType() {
        return type;
    }
}
