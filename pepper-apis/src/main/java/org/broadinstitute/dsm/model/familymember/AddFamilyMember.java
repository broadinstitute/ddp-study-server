package org.broadinstitute.dsm.model.familymember;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDataDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.Study;
import org.broadinstitute.dsm.model.participant.data.AddFamilyMemberPayload;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberDetails;
import org.broadinstitute.dsm.model.participant.data.ParticipantData;
import org.broadinstitute.dsm.model.rgp.RgpAddFamilyMember;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddFamilyMember {

    private static final Logger logger = LoggerFactory.getLogger(AddFamilyMember.class);

    protected AddFamilyMemberPayload addFamilyMemberPayload;
    protected ParticipantData participantData;
    protected int ddpInstanceId;
    protected DDPInstanceDao ddpInstanceDao;
    protected String studyGuid;
    protected String ddpParticipantId;

    protected AddFamilyMember(AddFamilyMemberPayload addFamilyMemberPayload) {
        this.addFamilyMemberPayload = Objects.requireNonNull(addFamilyMemberPayload);
        this.participantData = new ParticipantData();
        ddpInstanceDao = new DDPInstanceDao();
        studyGuid = addFamilyMemberPayload.getRealm().orElseThrow();
        ddpParticipantId = addFamilyMemberPayload.getParticipantId().orElseThrow();
        ddpInstanceId = ddpInstanceDao.getDDPInstanceIdByGuid(studyGuid);
    }

    public long add() {
        prepareFamilyMemberData();
        copyProbandData();
        addDefaultValueToData();
        long createdParticipantDataId = this.participantData.insertParticipantData(
                new UserDao().get(addFamilyMemberPayload.getUserId().orElse(0)).flatMap(UserDto::getEmail).orElse("SYSTEM"));
        exportDataToEs();
        return createdParticipantDataId;
    }

    protected void prepareFamilyMemberData() {
        FamilyMemberDetails familyMemberDetails = addFamilyMemberPayload.getData().orElseThrow();
        String fieldTypeId =  studyGuid + ParticipantData.FIELD_TYPE_PARTICIPANTS;
        participantData.setDdpParticipantId(ddpParticipantId);
        participantData.setDdpInstanceId(ddpInstanceId);
        participantData.setFieldTypeId(fieldTypeId);
        familyMemberDetails.setFamilyId(addFamilyMemberPayload.getOrGenerateFamilyId());
        familyMemberDetails.setCollaboratorParticipantId(addFamilyMemberPayload.generateCollaboratorParticipantId());
        if (FamilyMemberConstants.MEMBER_TYPE_SELF.equalsIgnoreCase(familyMemberDetails.getMemberType()))
            familyMemberDetails.setEmail(ParticipantUtil.getParticipantEmailById(
                    ddpInstanceDao.getEsParticipantIndexByStudyGuid(studyGuid).orElse(""),
                    addFamilyMemberPayload.getParticipantId().orElse("")));
        this.participantData.setData(familyMemberDetails.toMap());
    }

    protected void copyProbandData() {
        boolean isCopyProband = addFamilyMemberPayload.getCopyProbandInfo().orElse(Boolean.FALSE);
        if (!isCopyProband || StringUtils.isBlank(addFamilyMemberPayload.getParticipantId().orElse(""))) return;
        Map<String, String> participantDataData = participantData.getData();
        if (Objects.isNull(participantDataData)) throw new NoSuchElementException();
        List<ParticipantDataDto> participantDataByParticipantId =
                participantData.getParticipantDataByParticipantId(addFamilyMemberPayload.getParticipantId().orElse(""));
        Optional<ParticipantDataDto> maybeProbandData = participantData.findProband(participantDataByParticipantId);
        Optional<ParticipantData> maybeParticipantData = maybeProbandData.map(ParticipantData::parseDto);
        maybeParticipantData.ifPresent(participantData -> participantData.getData().forEach(participantDataData::putIfAbsent));
    }

    protected void addDefaultValueToData() {
        participantData.addDefaultOptionsValueToData(getDefaultValues());
    }

    public void exportDataToEs() {
        boolean isCopyProband = addFamilyMemberPayload.getCopyProbandInfo().orElse(Boolean.FALSE);
        if (isCopyProband) {
            exportProbandDataForFamilyMemberToEs();
        } else {
            exportDefaultWorkflowsForFamilyMemberToES();
        }
    }

    protected void exportProbandDataForFamilyMemberToEs() {
        List<FieldSettingsDto> fieldSettingsByInstanceIdAndColumns =
                getFieldSettingsDtosByInstanceIdAndColumns();
        FieldSettings fieldSettings = new FieldSettings();
        logger.info("Starting exporting copied proband data to family member into ES");
        fieldSettingsByInstanceIdAndColumns.forEach(fieldSettingsDto -> {
            if (!fieldSettings.isElasticExportWorkflowType(fieldSettingsDto)) return;
            WorkflowForES instanceWithStudySpecificData =
                    WorkflowForES.createInstanceWithStudySpecificData(
                            DDPInstance.getDDPInstance(studyGuid), addFamilyMemberPayload.getParticipantId().get(),
                            fieldSettingsDto.getColumnName(),
                            participantData.getData().get(fieldSettingsDto.getColumnName()),
                            new WorkflowForES.StudySpecificData(
                                    addFamilyMemberPayload.getData().get().getCollaboratorParticipantId(),
                                    addFamilyMemberPayload.getData().get().getFirstName(),
                                    addFamilyMemberPayload.getData().get().getLastName()));
            ElasticSearchUtil.writeWorkflow(instanceWithStudySpecificData, false);
        });
    }

    protected void exportDefaultWorkflowsForFamilyMemberToES() {
        logger.info("Exporting workflow for family member of participant: " + ddpParticipantId + " to ES");
        getDefaultOptionsByElasticWorkflow(ddpInstanceId).forEach((col, val) -> {
            WorkflowForES instanceWithStudySpecificData =
                    WorkflowForES.createInstanceWithStudySpecificData(DDPInstance.getDDPInstanceById(ddpInstanceId), ddpParticipantId, col, val,
                            new WorkflowForES.StudySpecificData(
                                    addFamilyMemberPayload.getData().get().getCollaboratorParticipantId(),
                                    addFamilyMemberPayload.getData().get().getFirstName(),
                                    addFamilyMemberPayload.getData().get().getLastName()));
            ElasticSearchUtil.writeWorkflow(instanceWithStudySpecificData, false);
        });
    }

    private Map<String, String> getDefaultOptionsByElasticWorkflow(int instanceId) {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettings fieldSettings = new FieldSettings();
        List<FieldSettingsDto> fieldSettingsByInstanceId = fieldSettingsDao.getFieldSettingsByInstanceId(instanceId);
        return fieldSettings.getColumnsWithDefaultOptionsFilteredByElasticExportWorkflow(fieldSettingsByInstanceId);
    }

    private List<FieldSettingsDto> getFieldSettingsDtosByInstanceIdAndColumns() {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        ArrayList<String> columns = new ArrayList<>(Objects.requireNonNull(participantData.getData()).keySet());
        return fieldSettingsDao.getFieldSettingsByInstanceIdAndColumns(
                ddpInstanceId,
                columns
        );
    }

    private Map<String, String> getDefaultValues() {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        FieldSettings fieldSettings = new FieldSettings();
        return fieldSettings.getColumnsWithDefaultValues(fieldSettingsDao.getOptionAndRadioFieldSettingsByInstanceId(ddpInstanceId));
    }

    public static AddFamilyMember instance(Study study, AddFamilyMemberPayload addFamilyMemberPayload) {
        AddFamilyMember addFamilyMember;
        switch (study) {
            case RGP:
                addFamilyMember = new RgpAddFamilyMember(addFamilyMemberPayload);
                break;
            default:
                addFamilyMember = new AddFamilyMember(addFamilyMemberPayload);
                break;
        }
        return addFamilyMember;
    }

}
