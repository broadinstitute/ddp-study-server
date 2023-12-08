package org.broadinstitute.dsm.model.phimanifest;

import lombok.Data;

@Data
public class PhiManifest {
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

}
