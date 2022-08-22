package org.broadinstitute.dsm.model.gp;

public class BSPKitInfo {

    private final int organismClassificationId;

    private String collaboratorParticipantId;
    private String collaboratorSampleId;
    private String sampleCollectionBarcode;
    private String gender;
    private String materialInfo;
    private String receptacleName;
    private String accessionNumber;
    private String realm;
    private String kitTypeName;

    public BSPKitInfo(String sampleCollectionBarcode, int organismClassificationId, String gender, String bspParticipantId,
                      String bspSampleId, String materialInfo, String receptacleName, String realm, String kitTypeName) {
        this.sampleCollectionBarcode = sampleCollectionBarcode;
        // note that organism is bsp's internal organismClassificationId, as per Damien
        this.organismClassificationId = organismClassificationId;
        this.gender = gender;
        this.collaboratorParticipantId = bspParticipantId;
        this.collaboratorSampleId = bspSampleId;
        this.materialInfo = materialInfo;
        this.receptacleName = receptacleName;
        this.realm = realm;
        this.kitTypeName = kitTypeName;
    }

    public BSPKitInfo(KitInfo kitInfo) {
        this.sampleCollectionBarcode = kitInfo.getSampleCollectionBarcode();
        // note that organism is bsp's internal organismClassificationId, as per Damien
        this.organismClassificationId = kitInfo.getOrganismClassificationId();
        this.gender = kitInfo.getGender();
        this.collaboratorParticipantId = kitInfo.getCollaboratorParticipantId();
        this.collaboratorSampleId = kitInfo.getCollaboratorSampleId();
        this.materialInfo = kitInfo.getMaterialInfo();
        this.receptacleName = kitInfo.getReceptacleName();
        this.realm = kitInfo.getRealm();
        this.kitTypeName = kitInfo.getKitTypeName();
    }

}
