package org.broadinstitute.dsm.model.phimanifest;

import lombok.Data;
import org.broadinstitute.dsm.route.phimanifest.PhiManifestReportRoute;

@Data
public class PhiManifest {
    private final String[] headers = new String[] {"Short Id", "First Name", "Last Name", "Proxy First Name", "Proxy Last Name",
            "Date of Birth", "Gender", "Somatic Assent Addendum Response", "Somatic Consent Tumor Pediatric Response",
            "somatic Consent Tumor Response", "Date of PX", "Facility Name", "Sample Type", "Accession Number", "Histology",
            "Block Id", "Tumor Collaborator Sample Id", "Tissue Site", "Sequencing Results", "Normal Manufacturer Barcode",
            "Normal Collaborator Sample Id", "Clinical Order Date", "Clinical Order Id", "Clinical PDO Number", "Order Status",
            "Order Status Date"};
    //profile
    String shortId;
    String firstName;
    String lastName;
    String proxyFirstName;
    String proxyLastName;
    String dateOfBirth;
    //either in activities or in Onc History
    String gender;
    //oncHistory and tissue
    String dateOfPx;
    String facility;
    String sampleType;
    String accessionNumber;
    String histology;
    //normal
    String mfBarcode;
    String normalCollaboratorSampleId;
    //tissue
    String blockId;
    String tumorCollaboratorSampleId;
    String tissueSite;
    String sequencingResults;
    //consent
    String somaticAssentAddendumResponse;
    String somaticConsentTumorPediatricResponse;
    String somaticConsentTumorResponse;
    //clinical order
    String clinicalOrderDate;
    String clinicalOrderId;
    String clinicalPdoNumber;
    String orderStatus;
    String orderStatusDate;

    public PhiManifestReportRoute.PhiManifestResponse toResponseArray(String ddpParticipantId, String sequencingOrderId) {
        String[][] report = new String[2][headers.length];

        String[] data = new String[headers.length];
        data[0] = this.getShortId();
        data[1] = this.getFirstName();
        data[2] = this.getLastName();
        data[3] = this.getProxyFirstName();
        data[4] = this.getProxyLastName();
        data[5] = this.getDateOfBirth();
        data[6] = this.getGender();
        data[7] = this.getSomaticAssentAddendumResponse();
        data[8] = this.getSomaticConsentTumorPediatricResponse();
        data[9] = this.getSomaticConsentTumorResponse();
        data[10] = this.getDateOfPx();
        data[11] = this.getFacility();
        data[12] = this.getSampleType();
        data[13] = this.getAccessionNumber();
        data[14] = this.getHistology();
        data[15] = this.getBlockId();
        data[16] = this.getTumorCollaboratorSampleId();
        data[17] = this.getTissueSite();
        data[18] = this.getSequencingResults();
        data[19] = this.getMfBarcode();
        data[20] = this.getNormalCollaboratorSampleId();
        data[21] = this.getClinicalOrderDate();
        data[22] = this.getClinicalOrderId();
        data[23] = this.getClinicalPdoNumber();
        data[24] = this.getOrderStatus();
        data[25] = this.getOrderStatusDate();

        report[0] = headers;
        report[1] = data;
        PhiManifestReportRoute.PhiManifestResponse phiManifestResponse = new PhiManifestReportRoute.PhiManifestResponse();
        phiManifestResponse.setData(report);
        phiManifestResponse.setOrderId(sequencingOrderId);
        phiManifestResponse.setDdpParticipantId(ddpParticipantId);
        return phiManifestResponse;
    }

}
