package org.broadinstitute.dsm.model.phimanifest;

import lombok.Data;
import org.broadinstitute.dsm.route.phimanifest.PhiManifestReportRoute;

@Data
public class PhiManifest {
    private final String[] headers =
            new String[] {"realm", "Participant ID", "Short Id", "Collaborator Participant ID", "First Name", "Last Name", "Date of Birth", "Proxy First Name",
                    "Proxy Last Name", "Gender", "Somatic Assent Addendum Response", "Somatic Consent Tumor Pediatric Response",
                    "somatic Consent Tumor Response", "Date of PX", "Facility Name", "TissueType", "Accession Number", "Histology",
                    "Block Id", "Tumor Collaborator Sample Id", "First SM ID", "Tissue Site", "Normal Manufacturer Barcode",
                    "Collection Date", "Normal Collaborator Sample Id", "Clinical Order Date", "Clinical Order Id",
                    "Clinical PDO Number", "Order Status", "Order Status Date", "Sequencing Results"};
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
    String tissueType;
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
    String collectionDate;
    String collaboratorParticipantId;

    public PhiManifestReportRoute.PhiManifestResponse toResponseArray(String ddpParticipantId, String sequencingOrderId) {
        String[][] report = new String[2][headers.length];

        String[] data = new String[headers.length];
        data[0] = this.getRealm();
        data[1] = this.getParticipantId();
        data[2] = this.getShortId();
        data[3] = this.getCollaboratorParticipantId();
        data[4] = this.getFirstName();
        data[5] = this.getLastName();
        data[6] = this.getDateOfBirth();
        data[7] = this.getProxyFirstName();
        data[8] = this.getProxyLastName();
        data[9] = this.getGender();
        data[10] = this.getSomaticAssentAddendumResponse();
        data[11] = this.getSomaticConsentTumorPediatricResponse();
        data[12] = this.getSomaticConsentTumorResponse();
        data[13] = this.getDateOfPx();
        data[14] = this.getFacility();
        data[15] = this.getTissueType();
        data[16] = this.getAccessionNumber();
        data[17] = this.getHistology();
        data[18] = this.getBlockId();
        data[19] = this.getTumorCollaboratorSampleId();
        data[20] = this.getFirstSmId();
        data[21] = this.getTissueSite();
        data[22] = this.getMfBarcode();
        data[23] = this.getCollectionDate();
        data[24] = this.getNormalCollaboratorSampleId();
        data[25] = this.getClinicalOrderDate();
        data[26] = this.getClinicalOrderId();
        data[27] = this.getClinicalPdoNumber();
        data[28] = this.getOrderStatus();
        data[29] = this.getOrderStatusDate();
        data[30] = this.getSequencingResults();

        report[0] = headers;
        report[1] = data;
        PhiManifestReportRoute.PhiManifestResponse phiManifestResponse = new PhiManifestReportRoute.PhiManifestResponse();
        phiManifestResponse.setData(report);
        phiManifestResponse.setOrderId(sequencingOrderId);
        phiManifestResponse.setDdpParticipantId(ddpParticipantId);
        return phiManifestResponse;
    }

}
