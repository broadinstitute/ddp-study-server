package org.broadinstitute.dsm.pubsub.study.osteo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.NewOsteoParticipant;
import org.broadinstitute.dsm.model.elastic.export.ElasticDataExportAdapter;
import org.broadinstitute.dsm.model.elastic.export.RequestPayload;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.pubsub.study.HasWorkflowStatusUpdate;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OsteoWorkflowStatusUpdate implements HasWorkflowStatusUpdate {

    private static final Gson GSON = new Gson();

    private final DDPInstance instance;
    private final String ddpParticipantId;
    private final int ddpInstanceIdAsInt;

    private final ParticipantDao participantDao;
    private final ParticipantRecordDao participantRecordDao;
    private final DDPInstitutionDao ddpInstitutionDao;
    private final MedicalRecordDao medicalRecordDao;
    private final ElasticDataExportAdapter elasticDataExportAdapter;
    private final ElasticSearchable elasticSearch;


    private OsteoWorkflowStatusUpdate(DDPInstance instance, String ddpParticipantId) {
        this.instance = instance;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceIdAsInt = instance.getDdpInstanceIdAsInt();
        this.participantDao = ParticipantDao.of();
        this.participantRecordDao = ParticipantRecordDao.of();
        this.ddpInstitutionDao = DDPInstitutionDao.of();
        this.medicalRecordDao = MedicalRecordDao.of();
        this.elasticSearch = new ElasticSearch();
        this.elasticDataExportAdapter = new ElasticDataExportAdapter();
        elasticDataExportAdapter.setRequestPayload(new RequestPayload(instance.getParticipantIndexES(), ddpParticipantId));
    }


    public static OsteoWorkflowStatusUpdate of(DDPInstance instance, String ddpParticipantId) {
        return new OsteoWorkflowStatusUpdate(instance, ddpParticipantId);
    }

    @Override
    public void update() {
        String ddpInstanceId = instance.getDdpInstanceId();
        boolean isParticipantInDb = MedicalRecordUtil.isParticipantInDB(ddpParticipantId, ddpInstanceId);
        if (isParticipantInDb) {
            Optional<ParticipantDto> maybeOldOsteoParticipant = participantDao.getParticipantByDdpParticipantIdAndDdpInstanceId(ddpParticipantId, Integer.parseInt(ddpInstanceId));
            Optional<Integer> maybeOldOsteoParticipantId = maybeOldOsteoParticipant.flatMap(ParticipantDto::getParticipantId);
            Optional<Integer> maybeNewOsteoParticipantId = maybeOldOsteoParticipant
                    .map(participantDto -> ParticipantDto.copy(ddpInstanceIdAsInt, participantDto))
                    .map(participantDao::create);
            Optional<ParticipantRecordDto> maybeOldOsteoParticipantRecord = maybeOldOsteoParticipantId.flatMap(participantRecordDao::getParticipantRecordByParticipantId);
            maybeOldOsteoParticipantRecord.ifPresent(participantRecord -> maybeNewOsteoParticipantId.ifPresent(participantId -> updateAndThenSaveNewParticipantRecord(participantRecord, participantId)));
            List<MedicalRecord> newOsteoMedicalRecords = maybeNewOsteoParticipantId.map(this::updateAndThenSaveInstitutionsAndMedicalRecords).orElseThrow();
            String oldOsteoDdpParticipantId = maybeOldOsteoParticipant.flatMap(ParticipantDto::getDdpParticipantId).orElseThrow();
            ElasticSearchParticipantDto esPtDto = elasticSearch.getParticipantById(instance.getParticipantIndexES(), oldOsteoDdpParticipantId);
            int newOsteoParticipantId = maybeNewOsteoParticipantId.orElseThrow();
            esPtDto.getDsm().ifPresent(esDsm -> updateEsDsm(newOsteoParticipantId, newOsteoMedicalRecords, esDsm));
            Map<String, Object> esPtDtoAsMap = ObjectMapperSingleton.readValue(GSON.toJson(esPtDto), new TypeReference<Map<String, Object>>() {});
            elasticDataExportAdapter.setSource(esPtDtoAsMap);
            elasticDataExportAdapter.export();
        }
    }

    private void updateEsDsm(long newOsteoParticipantId, List<MedicalRecord> newOsteoMedicalRecords, ESDsm dsm) {
        dsm.getParticipant().ifPresent(oldOsteoPt -> dsm.setNewOsteoParticipant(NewOsteoParticipant.copy(oldOsteoPt, newOsteoParticipantId, ddpInstanceIdAsInt)));
        List<MedicalRecord> oldOsteoMedicalRecords = dsm.getMedicalRecord();
        List<MedicalRecord> updatedMedicalRecords = Stream.concat(oldOsteoMedicalRecords.stream(), newOsteoMedicalRecords.stream()).collect(Collectors.toList());
        dsm.setMedicalRecord(updatedMedicalRecords);
    }

    private List<MedicalRecord> updateAndThenSaveInstitutionsAndMedicalRecords(int newOsteoParticipantId) {
        List<MedicalRecord> newOsteoMedicalRecords = new ArrayList<>();
        List<MedicalRecord> oldOsteoMedicalRecords = MedicalRecord.getMedicalRecordsByInstanceNameAndDdpParticipantId(instance.getName(), ddpParticipantId);
        oldOsteoMedicalRecords.forEach(medicalRecord -> {
            int newOsteoInstitutionId = updateAndThenSaveNewInstitution(newOsteoParticipantId, medicalRecord.getInstitutionId());
            medicalRecord.setInstitutionId(newOsteoInstitutionId);
            int newMedicalRecordId = medicalRecordDao.create(medicalRecord);
            MedicalRecord newOsteoMedicalRecord = MedicalRecord.copy(newOsteoInstitutionId, newMedicalRecordId, medicalRecord);
            newOsteoMedicalRecords.add(newOsteoMedicalRecord);
        });
        return newOsteoMedicalRecords;
    }

    private int updateAndThenSaveNewInstitution(int newOsteoParticipantId, long institutionId) {
        return ddpInstitutionDao.get(institutionId)
                .map(oldOsteoInstitution -> DDPInstitutionDto.copy(newOsteoParticipantId, oldOsteoInstitution))
                .map(ddpInstitutionDao::create)
                .orElseThrow();
    }

    private void updateAndThenSaveNewParticipantRecord(ParticipantRecordDto oldOsteoParticipantRecord, int newOsteoParticipantId) {
        ParticipantRecordDto participantRecordDto = ParticipantRecordDto.copy(newOsteoParticipantId, oldOsteoParticipantRecord);
        participantRecordDao.create(participantRecordDto);
    }

}
