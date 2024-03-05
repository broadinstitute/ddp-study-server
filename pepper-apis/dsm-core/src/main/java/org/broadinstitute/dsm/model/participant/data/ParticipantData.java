package org.broadinstitute.dsm.model.participant.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class ParticipantData {

    public static final String FIELD_TYPE_PARTICIPANTS = "_PARTICIPANTS";
    private static final Logger logger = LoggerFactory.getLogger(ParticipantData.class);
    private long participantDataId;

    private String ddpParticipantId;

    private int ddpInstanceId;

    private String fieldTypeId;

    private Map<String, String> data;
    @JsonIgnore
    private transient Dao dataAccess;

    public ParticipantData() {

    }

    public ParticipantData(Dao dao) {
        dataAccess = dao;
    }

    public ParticipantData(long participantDataId, String ddpParticipantId, int ddpInstanceId, String fieldTypeId,
                           Map<String, String> data) {
        this.participantDataId = participantDataId;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.fieldTypeId = fieldTypeId;
        this.data = data;
    }

    public static ParticipantData parseDto(@NonNull org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData participantData) {
        return new ParticipantData(
                participantData.getParticipantDataId(),
                participantData.getDdpParticipantId().orElse(StringUtils.EMPTY),
                participantData.getDdpInstanceId(),
                participantData.getFieldTypeId().orElse(StringUtils.EMPTY),
                ObjectMapperSingleton.readValue(participantData.getData().orElse(StringUtils.EMPTY), new TypeReference<Map<String,
                        String>>() {
                })
        );
    }

    public static List<ParticipantData> parseDtoList(
            @NonNull List<org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData> participantDataList) {
        List<ParticipantData> participantData = new ArrayList<>();
        participantDataList.forEach(dto -> participantData.add(parseDto(dto)));
        return participantData;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public void setData(String ddpParticipantId, int ddpInstanceId, String fieldTypeId, Map<String, String> data) {
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.fieldTypeId = fieldTypeId;
        this.data = data;
    }

    public List<org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData> getParticipantDataByParticipantId(String ddpParticipantId) {
        if (StringUtils.isBlank(ddpParticipantId)) {
            return Collections.emptyList();
        }
        ParticipantDataDao dataAccess = (ParticipantDataDao) setDataAccess(new ParticipantDataDao());
        return dataAccess.getParticipantData(ddpParticipantId);
    }

    private Dao setDataAccess(Dao dao) {
        this.dataAccess = dao;
        return this.dataAccess;
    }

    public void addDefaultOptionsValueToData(@NonNull Map<String, String> columnsWithDefaultOptions) {
        columnsWithDefaultOptions.forEach((column, option) -> {
            Map<String, String> data = this.getData();
            data.putIfAbsent(column, option);
            setData(data);
        });
    }

    public long insertParticipantData(String userEmail) {
        dataAccess = new ParticipantDataDao();
        org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData participantData =
                new org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData.Builder()
                        .withDdpParticipantId(this.ddpParticipantId)
                        .withDdpInstanceId(this.ddpInstanceId)
                        .withFieldTypeId(this.fieldTypeId)
                        .withData(ObjectMapperSingleton.writeValueAsString(this.data, false))
                        .withLastChanged(System.currentTimeMillis())
                        .withChangedBy(userEmail)
                        .build();
        if (isRelationshipIdExists()) {
            throw new DsmInternalError(String.format("Family member with Relationship ID %s already exists", getRelationshipId()));
        }
        int createdDataKey = dataAccess.create(participantData);
        if (createdDataKey < 1) {
            throw new DsmInternalError("Could not insert participant data for participant " + this.ddpParticipantId);
        }
        participantData.setParticipantDataId(createdDataKey);
        logger.info("Successfully inserted data for participant {} in db", this.ddpParticipantId);

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceId(this.ddpInstanceId).orElseThrow();
        String participantGuid = Exportable.getParticipantGuid(this.ddpParticipantId, ddpInstanceDto.getEsParticipantIndex());

        try {
            UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_DATA_ALIAS, participantData, ddpInstanceDto,
                            ESObjectConstants.PARTICIPANT_DATA_ID, ESObjectConstants.DOC_ID,
                            participantGuid, new PutToNestedScriptBuilder())
                    .export();
        } catch (Exception e) {
            throw new DsmInternalError("Error inserting ES participant data for participant " + participantGuid);
        }
        return createdDataKey;
    }

    public boolean isRelationshipIdExists() {
        List<String> participantRelationshipIds =
                parseDtoList(((ParticipantDataDao) dataAccess).getParticipantData(this.ddpParticipantId)).stream()
                        .map(pData -> {
                            Map<String, String> familyMemberData = pData.getData();
                            boolean hasRelationshipId = familyMemberData.containsKey(FamilyMemberConstants.RELATIONSHIP_ID);
                            if (hasRelationshipId) {
                                return familyMemberData.get(FamilyMemberConstants.RELATIONSHIP_ID);
                            }
                            return StringUtils.EMPTY;
                        })
                        .collect(Collectors.toList());
        return participantRelationshipIds.contains(getRelationshipId());
    }

    String getRelationshipId() {
        return this.getData().getOrDefault(FamilyMemberConstants.RELATIONSHIP_ID, null);
    }

    public Optional<org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData> findProband(
            List<org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData> participantDataList) {
        return Objects.requireNonNull(participantDataList).stream()
                .filter(participantDataDto -> {
                    Map<String, String> participantDataMap = ObjectMapperSingleton.readValue(participantDataDto.getData()
                                    .orElse(StringUtils.EMPTY),
                            new TypeReference<Map<String,
                                    String>>() {
                            });
                    return FamilyMemberConstants.MEMBER_TYPE_SELF.equals(participantDataMap.get(FamilyMemberConstants.MEMBER_TYPE));
                })
                .findFirst();
    }

    public boolean hasFamilyMemberApplicantEmail() {
        if (Objects.isNull(this.data) || StringUtils.isBlank(this.ddpParticipantId)) {
            return false;
        }
        String familyMemberEmail = this.getData().get(FamilyMemberConstants.EMAIL);
        String esParticipantIndex = new DDPInstanceDao().getEsParticipantIndexByInstanceId(ddpInstanceId).orElse(StringUtils.EMPTY);
        String applicantEmail = ParticipantUtil.getParticipantEmailById(esParticipantIndex, this.ddpParticipantId);
        return applicantEmail.equalsIgnoreCase(familyMemberEmail);
    }

    public boolean hasFamilyMemberApplicantEmail(Profile applicantProfile) {
        if (Objects.isNull(this.data) || StringUtils.isBlank(this.ddpParticipantId)) {
            return false;
        }
        String familyMemberEmail = this.getData().get(FamilyMemberConstants.EMAIL);
        String applicantEmail = StringUtils.defaultIfBlank(applicantProfile.getEmail(), StringUtils.EMPTY);
        return applicantEmail.equalsIgnoreCase(familyMemberEmail);
    }
}
