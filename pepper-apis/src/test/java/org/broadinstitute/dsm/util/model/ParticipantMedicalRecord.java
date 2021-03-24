package org.broadinstitute.dsm.util.model;

public class ParticipantMedicalRecord {

    private String instanceName;
    private String ddpParticipantId;
    private String ddpInstitutionId;
    private String institutionType;
    private String oncHistCreated;
    private String oncHistReviewed;
    private String medInstitutionName;
    private String medInstitutionContact;
    private String medInstitutionPhone;
    private String medInstitutionFax;
    private String medFaxSent;
    private String medFaxConfirmed;
    private String medMRReceived;
    private String medMRDocument;
    private String medMRProblem;
    private String medMRProblemText;
    private String medMRUnableObtain;
    private String medMRDuplicate;
    private String medMRNotes;
    private String medReviewMR;

    public ParticipantMedicalRecord(String instanceName, String ddpParticipantId, String ddpInstitutionId, String institutionType,
                                    String oncHistCreated, String oncHistReviewed, String medInstitutionName,
                                    String medInstitutionContact, String medInstitutionPhone, String medInstitutionFax,
                                    String medFaxSent, String medFaxConfirmed, String medMRReceived, String medMRDocument,
                                    String medMRProblem, String medMRProblemText, String medMRUnableObtain,
                                    String medMRDuplicate, String medMRNotes, String medReviewMR) {
        this.instanceName = instanceName;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstitutionId = ddpInstitutionId;
        this.institutionType = institutionType;
        this.oncHistCreated = oncHistCreated;
        this.oncHistReviewed = oncHistReviewed;
        this.medInstitutionName = medInstitutionName;
        this.medInstitutionContact = medInstitutionContact;
        this.medInstitutionPhone = medInstitutionPhone;
        this.medInstitutionFax = medInstitutionFax;
        this.medFaxSent = medFaxSent;
        this.medFaxConfirmed = medFaxConfirmed;
        this.medMRReceived = medMRReceived;
        this.medMRDocument = medMRDocument;
        this.medMRProblem = medMRProblem;
        this.medMRProblemText = medMRProblemText;
        this.medMRUnableObtain = medMRUnableObtain;
        this.medMRDuplicate = medMRDuplicate;
        this.medMRNotes = medMRNotes;
        this.medReviewMR = medReviewMR;
    }

    public String getKey(){
        return ddpParticipantId + "_" + ddpInstitutionId + "_" + institutionType;
    }
    public String getInstanceName() {
        return instanceName;
    }

    public String getDdpParticipantId() {
        return ddpParticipantId;
    }

    public String getDdpInstitutionId() {
        return ddpInstitutionId;
    }

    public String getInstitutionType() {
        return institutionType;
    }

    public String getOncHistCreated() {
        return oncHistCreated;
    }

    public String getOncHistReviewed() {
        return oncHistReviewed;
    }

    public String getMedInstitutionName() {
        return medInstitutionName;
    }

    public String getMedInstitutionContact() {
        return medInstitutionContact;
    }

    public String getMedInstitutionPhone() {
        return medInstitutionPhone;
    }

    public String getMedInstitutionFax() {
        return medInstitutionFax;
    }

    public String getMedFaxSent() {
        return medFaxSent;
    }

    public String getMedFaxConfirmed() {
        return medFaxConfirmed;
    }

    public String getMedMRReceived() {
        return medMRReceived;
    }

    public String getMedMRDocument() {
        return medMRDocument;
    }

    public String getMedMRProblem() {
        return medMRProblem;
    }

    public String getMedMRProblemText() {
        return medMRProblemText;
    }

    public String getMedMRUnableObtain() {
        return medMRUnableObtain;
    }

    public String getMedMRDuplicate() {
        return medMRDuplicate;
    }

    public String getMedMRNotes() {
        return medMRNotes;
    }

    public String getMedReviewMR() {
        return medReviewMR;
    }
}
