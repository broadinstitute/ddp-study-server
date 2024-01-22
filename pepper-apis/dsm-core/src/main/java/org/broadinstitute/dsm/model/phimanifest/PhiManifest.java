package org.broadinstitute.dsm.model.phimanifest;

import lombok.Data;
import org.broadinstitute.dsm.route.phimanifest.PhiManifestReportRoute;

@Data
public class PhiManifest {
    private final String[] headers =
            new String[] {"realm", "Participant ID", "Short Id", "First Name", "Last Name", "Date of Birth", "Proxy First Name",
                    "Proxy Last Name", "Gender", "Somatic Assent Addendum Response", "Somatic Consent Tumor Pediatric Response",
                    "somatic Consent Tumor Response", "Date of PX", "Facility Name", "Sample Type", "Accession Number", "Histology",
                    "Block Id", "Tumor Collaborator Sample Id", "First SM ID", "Tissue Site", "Sequencing Results",
                    "Normal Manufacturer Barcode", "Normal Collaborator Sample Id", "Clinical Order Date", "Clinical Order Id",
                    "Clinical PDO Number", "Order Status", "Order Status Date"};
    //profile
    String realm;
    String participantId;
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
    String firstSmId;
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
        data[0] = this.getRealm();
        data[1] = this.getParticipantId();
        data[2] = this.getShortId();
        data[3] = this.getFirstName();
        data[4] = this.getLastName();
        data[5] = this.getDateOfBirth();
        data[6] = this.getProxyFirstName();
        data[7] = this.getProxyLastName();
        data[8] = this.getGender();
        data[9] = this.getSomaticAssentAddendumResponse();
        data[10] = this.getSomaticConsentTumorPediatricResponse();
        data[11] = this.getSomaticConsentTumorResponse();
        data[12] = this.getDateOfPx();
        data[13] = this.getFacility();
        data[14] = this.getSampleType();
        data[15] = this.getAccessionNumber();
        data[16] = this.getHistology();
        data[17] = this.getBlockId();
        data[18] = this.getTumorCollaboratorSampleId();
        data[19] = this.getFirstSmId();
        data[20] = this.getTissueSite();
        data[21] = this.getSequencingResults();
        data[22] = this.getMfBarcode();
        data[23] = this.getNormalCollaboratorSampleId();
        data[24] = this.getClinicalOrderDate();
        data[25] = this.getClinicalOrderId();
        data[26] = this.getClinicalPdoNumber();
        data[27] = this.getOrderStatus();
        data[28] = this.getOrderStatusDate();


        report[0] = headers;
        report[1] = data;
        PhiManifestReportRoute.PhiManifestResponse phiManifestResponse = new PhiManifestReportRoute.PhiManifestResponse();
        phiManifestResponse.setData(report);
        phiManifestResponse.setOrderId(sequencingOrderId);
        phiManifestResponse.setDdpParticipantId(ddpParticipantId);
        return phiManifestResponse;
    }

}
