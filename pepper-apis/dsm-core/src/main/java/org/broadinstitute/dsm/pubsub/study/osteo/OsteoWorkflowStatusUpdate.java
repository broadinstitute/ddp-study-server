package org.broadinstitute.dsm.pubsub.study.osteo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter.NEW_OSTEO_INSTANCE_NAME;

public class OsteoWorkflowStatusUpdate implements HasWorkflowStatusUpdate {

    private static final Logger logger = LoggerFactory.getLogger(OsteoWorkflowStatusUpdate.class);
    private static final Gson GSON = new Gson();
    private static final String NEW_OSTEO_COHORT_TAG_NAME = "OS PE-CGS";

    private final DDPInstance instance;
    private final String ddpParticipantId;
    private final int newOsteoInstanceId;
    private final CohortTag newCohortTag;

    private final ParticipantDao participantDao;
    private final ParticipantRecordDao participantRecordDao;
    private final DDPInstitutionDao ddpInstitutionDao;
    private final MedicalRecordDao medicalRecordDao;
    private final CohortTagDao cohortTagDao;

    private final ElasticDataExportAdapter elasticDataExportAdapter;
    private final ElasticSearchable elasticSearch;


    private OsteoWorkflowStatusUpdate(DDPInstance instance, String ddpParticipantId) {
        this.instance = instance;
        this.ddpParticipantId = ddpParticipantId;
        this.participantDao = ParticipantDao.of();
        this.participantRecordDao = ParticipantRecordDao.of();
        this.ddpInstitutionDao = DDPInstitutionDao.of();
        this.medicalRecordDao = MedicalRecordDao.of();
        this.cohortTagDao = new CohortTagDaoImpl();
        this.elasticSearch = new ElasticSearch();
        this.elasticDataExportAdapter = new ElasticDataExportAdapter();
        elasticDataExportAdapter.setRequestPayload(new RequestPayload(instance.getParticipantIndexES(), ddpParticipantId));
        this.newOsteoInstanceId = DDPInstanceDao.of().getDDPInstanceIdByInstanceName(NEW_OSTEO_INSTANCE_NAME);
        this.newCohortTag = new CohortTag(NEW_OSTEO_COHORT_TAG_NAME, ddpParticipantId, newOsteoInstanceId);
    }


    public static OsteoWorkflowStatusUpdate of(DDPInstance instance, String ddpParticipantId) {
        return new OsteoWorkflowStatusUpdate(instance, ddpParticipantId);
    }

    @Override
    public void update() {
        logger.info(String.format("Running workflow updates for %s", NEW_OSTEO_INSTANCE_NAME));
        String ddpInstanceId = instance.getDdpInstanceId();
        boolean isParticipantInDb = MedicalRecordUtil.isParticipantInDB(ddpParticipantId, ddpInstanceId);
        if (isParticipantInDb) {
            logger.info(String.format("Updating values in db for %s", NEW_OSTEO_INSTANCE_NAME));
            Optional<ParticipantDto> maybeOldOsteoParticipant = participantDao.getParticipantByDdpParticipantIdAndDdpInstanceId(ddpParticipantId, Integer.parseInt(ddpInstanceId));
            Optional<Integer> maybeOldOsteoParticipantId = maybeOldOsteoParticipant.flatMap(ParticipantDto::getParticipantId);
            Optional<Integer> maybeNewOsteoParticipantId = maybeOldOsteoParticipant
                    .map(participantDto -> ParticipantDto.copy(newOsteoInstanceId, participantDto))
                    .map(participantDao::create);
            cohortTagDao.create(newCohortTag);
            Optional<ParticipantRecordDto> maybeOldOsteoParticipantRecord = maybeOldOsteoParticipantId.flatMap(participantRecordDao::getParticipantRecordByParticipantId);
            maybeOldOsteoParticipantRecord.ifPresent(participantRecord -> maybeNewOsteoParticipantId.ifPresent(participantId -> updateAndThenSaveNewParticipantRecord(participantRecord, participantId)));
            List<MedicalRecord> newOsteoMedicalRecords = maybeNewOsteoParticipantId.map(this::updateAndThenSaveInstitutionsAndMedicalRecords).orElseThrow();
            String oldOsteoDdpParticipantId = maybeOldOsteoParticipant.flatMap(ParticipantDto::getDdpParticipantId).orElseThrow();
            ElasticSearchParticipantDto esPtDto = elasticSearch.getParticipantById(instance.getParticipantIndexES(), oldOsteoDdpParticipantId);
            int newOsteoParticipantId = maybeNewOsteoParticipantId.orElseThrow();
            esPtDto.getDsm().ifPresent(esDsm -> updateEsDsm(newOsteoParticipantId, newOsteoMedicalRecords, esDsm));
            Map<String, Object> esPtDtoAsMap = ObjectMapperSingleton.readValue(GSON.toJson(esPtDto), new TypeReference<Map<String, Object>>() {});
            writeDataToES(esPtDtoAsMap);
        } else {
            logger.info(String.format("Participant with id %s does not exist", ddpParticipantId));
        }
    }

    private void writeDataToES(Map<String, Object> esPtDtoAsMap) {
        logger.info(String.format("Exporting values in ES for %s", NEW_OSTEO_INSTANCE_NAME));
        elasticDataExportAdapter.setSource(esPtDtoAsMap);
        elasticDataExportAdapter.export();
    }

    private void updateEsDsm(long newOsteoParticipantId, List<MedicalRecord> newOsteoMedicalRecords, ESDsm dsm) {
        dsm.getParticipant().ifPresent(oldOsteoPt -> dsm.setNewOsteoParticipant(NewOsteoParticipant.copy(oldOsteoPt, newOsteoParticipantId, newOsteoInstanceId)));
        dsm.setCohortTag(Stream.concat(dsm.getCohortTag().stream(), Stream.of(newCohortTag)).collect(Collectors.toList()));
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
            medicalRecord.setMedicalRecordId(newMedicalRecordId);
            newOsteoMedicalRecords.add(medicalRecord);
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
