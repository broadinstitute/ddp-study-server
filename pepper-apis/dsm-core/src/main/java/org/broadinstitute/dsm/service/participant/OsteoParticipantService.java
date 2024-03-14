package org.broadinstitute.dsm.service.participant;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;

@Slf4j
public class OsteoParticipantService {
    public static final String OSTEO1_INSTANCE_NAME = "Osteo";
    public static final String OSTEO2_INSTANCE_NAME = "osteo2";
    public static final String OSTEO2_COHORT_TAG_NAME = "OS PE-CGS";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final ParticipantDao participantDao = new ParticipantDao();
    private static final ParticipantRecordDao participantRecordDao = new ParticipantRecordDao();
    private static final DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
    private static final MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
    private static final CohortTagDao cohortTagDao = new CohortTagDaoImpl();
    @Getter
    private final DDPInstanceDto osteo1Instance;
    @Getter
    private final DDPInstanceDto osteo2Instance;
    private final List<String> osteoInstanceNames;
    private final ElasticSearchService elasticSearchService;

    public OsteoParticipantService() {
        this(OSTEO1_INSTANCE_NAME, OSTEO2_INSTANCE_NAME);
    }

    public OsteoParticipantService(String osteo1InstanceName, String osteo2InstanceName) {
        // these are like invariant properties in non-test contexts, so not expecting the throw
        // (and thus fine to initialize them in the constructor)
        osteo1Instance = ddpInstanceDao.getDDPInstanceByInstanceName(osteo1InstanceName).orElseThrow();
        osteo2Instance = ddpInstanceDao.getDDPInstanceByInstanceName(osteo2InstanceName).orElseThrow();
        osteoInstanceNames = List.of(osteo1InstanceName.toLowerCase(), osteo2InstanceName.toLowerCase());
        elasticSearchService = new ElasticSearchService();
    }

    /**
     * Set default values for newly registered OS PE-CGS (OS2/osteo2) participant (not re-consented osteo1 participant)
     *
     * @param participantDto participant ElasticSearch data, including DSM entity
     */
    public void setOsteo2DefaultData(String ddpParticipantId, ElasticSearchParticipantDto participantDto) {
        // expecting participant to have a DSM entity
        Dsm dsm = participantDto.getDsm().orElseThrow();

        // add a cohort tag for the participant
        CohortTag newCohortTag =
                new CohortTag(OSTEO2_COHORT_TAG_NAME, ddpParticipantId, osteo2Instance.getDdpInstanceId());
        int newCohortTagId = cohortTagDao.create(newCohortTag);
        newCohortTag.setCohortTagId(newCohortTagId);

        // new participant so this is the first tag
        dsm.setCohortTag(List.of(newCohortTag));
        ElasticSearchService.updateDsm(ddpParticipantId, dsm, osteo2Instance.getEsParticipantIndex());
    }

    /**
     * Copy osteo1 participant data to osteo2 for osteo1 participant who consented to OS PE-CGS (OS2/osteo2)
     */
    public void initializeOsteo2Participant(String ddpParticipantId) {
        log.info("Creating new {} participant data for existing {} participant {}",
                osteo2Instance.getInstanceName(), osteo1Instance.getInstanceName(), ddpParticipantId);

        // ensure we can get what we need before committing anything
        int osteo1InstanceId = osteo1Instance.getDdpInstanceId();
        ParticipantDto osteo1Participant = participantDao
                .getParticipantForInstance(ddpParticipantId, osteo1InstanceId)
                .orElseThrow(() -> new DsmInternalError("Participant %s not found for instance %s"
                        .formatted(ddpParticipantId, osteo1Instance.getInstanceName())));
        int osteo1ParticipantId = osteo1Participant.getRequiredParticipantId();

        // there should already be ES DSM data in osteo2 and osteo1 index for this participant
        Dsm osteo2Dsm =
                elasticSearchService.getRequiredDsmData(ddpParticipantId, osteo2Instance.getEsParticipantIndex());
        Dsm osteo1Dsm =
                elasticSearchService.getRequiredDsmData(ddpParticipantId, osteo1Instance.getEsParticipantIndex());
        Participant esParticipant = osteo1Dsm.getParticipant().orElseThrow(() ->
                new DsmInternalError("ES Participant data missing for participant %s".formatted(ddpParticipantId)));

        // start copying participant data
        int osteo2InstanceId = osteo2Instance.getDdpInstanceId();
        ParticipantDto osteo2Participant = osteo1Participant.clone();
        osteo2Participant.setDdpInstanceId(osteo2InstanceId);
        int osteo2ParticipantId = participantDao.create(osteo2Participant);

        // TODO: we should probably get DSM participant data directly from the DB instead of ES, but the Participant
        // class needs to be updated to support that. And if the osteo1 ES data is out of sync with the DB, we should
        // probably update that too. Perhaps in the future we will be more confident about the fidelity of ES data. -DC
        esParticipant.setParticipantId((long)osteo2ParticipantId);
        esParticipant.setDdpInstanceId(osteo2InstanceId);
        osteo2Dsm.setParticipant(esParticipant);

        // ParticipantRecord is not in ES
        participantRecordDao.getParticipantRecordByParticipantId(osteo1Participant.getRequiredParticipantId())
                .ifPresent(participantRecord -> {
                    ParticipantRecordDto clonedRecord = participantRecord.clone();
                    clonedRecord.setParticipantId(osteo2ParticipantId);
                    participantRecordDao.create(clonedRecord);
                });

        osteo2Dsm.setMedicalRecord(copyMedicalRecords(osteo1ParticipantId, osteo2ParticipantId, osteo2InstanceId));

        CohortTag cohortTag = new CohortTag(OSTEO2_COHORT_TAG_NAME, ddpParticipantId, osteo2InstanceId);
        int newCohortTagId = cohortTagDao.create(cohortTag);
        cohortTag.setCohortTagId(newCohortTagId);

        List<CohortTag> cohortTags = osteo2Dsm.getCohortTag();
        cohortTags.add(cohortTag);
        osteo2Dsm.setCohortTag(cohortTags);

        ElasticSearchService.updateDsm(ddpParticipantId, osteo2Dsm, osteo2Instance.getEsParticipantIndex());
    }

    private List<MedicalRecord> copyMedicalRecords(int osteo1ParticipantId, int osteo2ParticipantId,
                                                   int osteo2InstanceId) {
        List<MedicalRecord> os2MedicalRecords = new ArrayList<>();
        List<MedicalRecord> medicalRecords = MedicalRecord.getMedicalRecordsForParticipant(osteo1ParticipantId);
        medicalRecords.forEach(medicalRecord -> {
            int newOsteoInstitutionId = copyInstitution(osteo2ParticipantId, medicalRecord.getInstitutionId());
            medicalRecord.setInstitutionId(newOsteoInstitutionId);
            medicalRecord.setDdpInstanceId(osteo2InstanceId);
            int newMedicalRecordId = medicalRecordDao.create(medicalRecord);
            medicalRecord.setMedicalRecordId(newMedicalRecordId);
            os2MedicalRecords.add(medicalRecord);
        });
        return os2MedicalRecords;
    }

    private int copyInstitution(int newOsteoParticipantId, int institutionId) {
        DDPInstitutionDto institution = ddpInstitutionDao.get(institutionId).orElseThrow(() ->
                new DsmInternalError("Institution not found for ID %d".formatted(institutionId)));

        DDPInstitutionDto clonedInstitution = institution.clone();
        clonedInstitution.setParticipantId(newOsteoParticipantId);
        return ddpInstitutionDao.create(clonedInstitution);
    }

    /**
     * Return true if participant is *only* in osteo1 study
     */
    public boolean isOnlyOsteo1Participant(String ddpParticipantId) {
        List<ParticipantDto> recs = participantDao.getParticipant(ddpParticipantId);
        return recs.size() == 1 && recs.get(0).getDdpInstanceId() == osteo1Instance.getDdpInstanceId();
    }

    public boolean isOsteoInstance(DDPInstanceDto ddpInstanceDto) {
        return osteoInstanceNames.contains(ddpInstanceDto.getInstanceName());
    }
}
