package org.broadinstitute.dsm.db.dto.kit;


import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ClinicalKitDto {

    private static final Logger logger = LoggerFactory.getLogger(ClinicalKitDto.class);
    @SerializedName("participant_id")
    String collaboratorParticipantId;
    @SerializedName("sample_id")
    String sampleId;
    @SerializedName("sample_collection")
    String sampleCollection;
    @SerializedName("material_type")
    String materialType;
    @SerializedName("vessel_type")
    String vesselType;
    @SerializedName("first_name")
    String firstName;
    @SerializedName("last_name")
    String lastName;
    @SerializedName("date_of_birth")
    String dateOfBirth;
    @SerializedName("sample_type")
    String sampleType;
    @SerializedName("gender")
    String gender;
    @SerializedName("accession_number")
    String accessionNumber;
    @SerializedName ("sample_collection_date")
    String collectionDate;
    @SerializedName("kit_label")
    String mfBarcode;

    public ClinicalKitDto(String collaboratorParticipantId, String sampleId, String sampleCollection, String materialType,
                          String vesselType,
                          String firstName, String lastName, String dateOfBirth, String sampleType, String gender, String accessionNumber,
                          String mfBarcode) {
        this.collaboratorParticipantId = collaboratorParticipantId;
        this.sampleId = sampleId;
        this.sampleCollection = sampleCollection;
        this.materialType = materialType;
        this.vesselType = vesselType;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.sampleType = sampleType;
        this.gender = gender;
        this.accessionNumber = accessionNumber;
        this.mfBarcode = mfBarcode;
    }

    public ClinicalKitDto() {
    }

    public void setSampleType(String kitType) {
        switch (kitType.toLowerCase()) {
            case "saliva":
                this.sampleType = "Normal";
                break;
            case "blood":
                this.sampleType = "N/A";
                break;
            default: //tissue
                this.sampleType = "Tumor";
                break;
        }
    }

    public void setGender(String genderString) {
        switch (genderString.toLowerCase()) {
            case "male":
                this.gender = "M";
                break;
            case "female":
                this.gender = "F";
                break;
            default: //intersex or prefer_not_answer
                this.gender = "U";
                break;
        }
    }

    public void setNecessaryParticipantDataToClinicalKit(String ddpParticipantId, DDPInstance ddpInstance) {
        logger.info("Setting PHI to clinical kit's accessioning data for {} in study {}", ddpParticipantId, ddpInstance.getName());

        ElasticSearchParticipantDto esParticipantDto =
                ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);

        try {
            this.setDateOfBirth(esParticipantDto.getDsm().map(Dsm::getDateOfBirth).orElse(""));
            this.setFirstName(esParticipantDto.getProfile().map(Profile::getFirstName).orElse(""));
            this.setLastName(esParticipantDto.getProfile().map(Profile::getLastName).orElse(""));
            this.setGender(ParticipantUtil.getParticipantGender(esParticipantDto, ddpInstance.getName(), ddpParticipantId));
            String shortId = esParticipantDto.getProfile().map(Profile::getHruid).orElse("");
            String collaboratorParticipantId =
                    KitRequestShipping.getCollaboratorParticipantId(ddpInstance.getBaseUrl(), ddpInstance.getDdpInstanceId(),
                            ddpInstance.isMigratedDDP(),
                            ddpInstance.getCollaboratorIdPrefix(), ddpParticipantId, shortId, null);
            this.setCollaboratorParticipantId(collaboratorParticipantId);
        } catch (Exception e) {
            throw new DsmInternalError(String.format("Error getting participant data for %s", ddpParticipantId), e);
        }
    }
}
