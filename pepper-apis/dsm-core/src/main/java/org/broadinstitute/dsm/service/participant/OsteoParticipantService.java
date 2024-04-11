package org.broadinstitute.dsm.service.participant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.Participant;
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
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.onchistory.OncHistoryService;
import org.broadinstitute.dsm.util.SystemUtil;


@Slf4j
public class OsteoParticipantService {
    public static final String OSTEO1_INSTANCE_NAME = "Osteo";
    public static final String OSTEO2_INSTANCE_NAME = "osteo2";
    public static final String OSTEO1_COHORT_TAG_NAME = "OS";
    public static final String OSTEO2_COHORT_TAG_NAME = "OS PE-CGS";
    private static final ParticipantDao participantDao = new ParticipantDao();
    private static final ParticipantRecordDao participantRecordDao = new ParticipantRecordDao();
    private static final DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
    private static final MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
    private static final CohortTagDao cohortTagDao = new CohortTagDaoImpl();
    @Getter
    private final DDPInstance osteo1Instance;
    @Getter
    private final DDPInstance osteo2Instance;
    private final List<String> osteoInstanceNames;
    private final ElasticSearchService elasticSearchService;

    public OsteoParticipantService() {
        this(OSTEO1_INSTANCE_NAME, OSTEO2_INSTANCE_NAME);
    }

    public OsteoParticipantService(String osteo1InstanceName, String osteo2InstanceName) {
        // these are like invariant properties in non-test contexts, so not expecting the throw
        // (and thus fine to initialize them in the constructor)
        osteo1Instance = DDPInstance.getDDPInstance(osteo1InstanceName);
        osteo2Instance = DDPInstance.getDDPInstance(osteo2InstanceName);
        osteoInstanceNames = List.of(osteo1InstanceName.toLowerCase(), osteo2InstanceName.toLowerCase());
        elasticSearchService = new ElasticSearchService();
    }

    /**
     * Set default values for a newly registered osteo1/osteo2 participant (not re-consented osteo1 participant)
     * NOTE: this code supports recreating OS1 participants in non-prod environments, since there are no new
     *  OS1 registrations in prod
     * @param participantDto participant ElasticSearch data, including DSM entity
     */
    public void setOsteoDefaultData(String ddpParticipantId, ElasticSearchParticipantDto participantDto) {
        if (isOsteo1Participant(participantDto)) {
            log.info("Creating an osteo1 cohort tag for participant {}", ddpParticipantId);
            createOsteoTag(participantDto, ddpParticipantId, osteo1Instance, OSTEO1_COHORT_TAG_NAME);
        } else {
            createOsteoTag(participantDto, ddpParticipantId, osteo2Instance, OSTEO2_COHORT_TAG_NAME);
        }
    }

    /**
     * Return true if new participant registration is for OS1.
     * The criteria for OS1 participant is:
     * 1. Has consent activity
     * 2. Does not have any consent activity with version > v1
     * So if a participant has no consent activities when this code is called,
     * they are considered OS2 participant.
     */
    protected boolean isOsteo1Participant(ElasticSearchParticipantDto participantDto) {
        List<Activities> activities = participantDto.getActivities();
        if (activities.isEmpty()) {
            throw new DsmInternalError("Participant activities missing for participant "
                    + participantDto.getParticipantId());
        }
        if (!hasConsentActivity(activities)) {
            return false;
        }
        if (activityHasLaterVersion(activities, DDPActivityConstants.ACTIVITY_CONSENT)) {
            return false;
        }
        if (activityHasLaterVersion(activities, DDPActivityConstants.ACTIVITY_PARENTAL_CONSENT)) {
            return false;
        }

        return !activityHasLaterVersion(activities, DDPActivityConstants.ACTIVITY_CONSENT_ASSENT);
    }

    protected boolean activityHasLaterVersion(List<Activities> activities, String activityCode) {
        return activities.stream().anyMatch(activity -> activityCode.equals(activity.getActivityCode())
                && !activity.getActivityVersion().equals("v1"));
    }

    protected boolean hasConsentActivity(List<Activities> activities) {
        Set<String> consentActivityCodes = Set.of(DDPActivityConstants.ACTIVITY_CONSENT,
                DDPActivityConstants.ACTIVITY_PARENTAL_CONSENT, DDPActivityConstants.ACTIVITY_CONSENT_ASSENT);
        return activities.stream().anyMatch(activity -> consentActivityCodes.contains(activity.getActivityCode()));
    }

    private static void createOsteoTag(ElasticSearchParticipantDto participantDto, String ddpParticipantId,
                                       DDPInstance osteoInstance, String tagName) {
        // invariant: participant should have a DSM entity
        Dsm dsm = participantDto.getDsm().orElseThrow();

        CohortTag newCohortTag =
                new CohortTag(tagName, ddpParticipantId, osteoInstance.getDdpInstanceIdAsInt());
        int newCohortTagId = cohortTagDao.create(newCohortTag);
        newCohortTag.setCohortTagId(newCohortTagId);

        // new participant so this is the first tag
        dsm.setCohortTag(List.of(newCohortTag));
        ElasticSearchService.updateDsm(ddpParticipantId, dsm, osteoInstance.getParticipantIndexES());
    }

    /**
     * Copy osteo1 participant data to osteo2 for osteo1 participant who consented to OS PE-CGS (OS2/osteo2)
     */
    public void initializeReconsentedParticipant(String ddpParticipantId) {
        log.info("Initializing re-consented osteo participant {}", ddpParticipantId);

        // ensure we can get what we need before committing anything
        int osteo1InstanceId = osteo1Instance.getDdpInstanceIdAsInt();
        Optional<ParticipantDto> osteo1Ptp = participantDao
                .getParticipantForInstance(ddpParticipantId, osteo1InstanceId);
        if (osteo1Ptp.isEmpty()) {
            log.info("Participant {} record not found for instance {}. Skipping re-consent initialization.",
                    ddpParticipantId, osteo1Instance.getName());
            return;
        }
        ParticipantDto osteo1Participant = osteo1Ptp.get();
        int osteo1ParticipantId = osteo1Participant.getRequiredParticipantId();
        log.info("Creating new {} participant data for existing {} participant {}",
                osteo2Instance.getName(), osteo1Instance.getName(), ddpParticipantId);

        // there should already be ES DSM data in osteo1 index for this participant
        Dsm osteo1Dsm =
                elasticSearchService.getRequiredDsmData(ddpParticipantId, osteo1Instance.getParticipantIndexES());
        Participant esParticipant = osteo1Dsm.getParticipant().orElseThrow(() ->
                new DsmInternalError("ES Participant data missing for participant %s".formatted(ddpParticipantId)));

        // there *should* be ES DSM data for osteo2, but make it if not
        String osteo2Index = osteo2Instance.getParticipantIndexES();
        ElasticSearchParticipantDto osteo2EsParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, osteo2Index);
        Optional<Dsm> dsm = osteo2EsParticipant.getDsm();
        Dsm osteo2Dsm = dsm.orElseGet(Dsm::new);

        // there should not be osteo2 Participant, MedicalRecord or ParticipantRecord yet
        // if the Participant does not exist, the other data will not exist either
        if (participantDao.getParticipantForInstance(ddpParticipantId, osteo2Instance.getDdpInstanceIdAsInt()).isPresent()) {
            throw new DsmInternalError("Participant %s already exists for instance %s"
                    .formatted(ddpParticipantId, osteo2Instance.getName()));
        }

        // start copying participant data
        int osteo2InstanceId = osteo2Instance.getDdpInstanceIdAsInt();
        int osteo2ParticipantId = copyParticipant(osteo1Participant, osteo2InstanceId);

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
        if (cohortTags.isEmpty()) {
            cohortTags = new ArrayList<>();
        }
        cohortTags.add(cohortTag);
        osteo2Dsm.setCohortTag(cohortTags);

        ElasticSearchService.updateDsm(ddpParticipantId, osteo2Dsm, osteo2Index);
        OncHistoryService.createEmptyOncHistory(osteo2ParticipantId, ddpParticipantId, osteo2Instance);
    }

    private int copyParticipant(ParticipantDto osteo1Participant, int osteo2InstanceId) {
        // do not copy any user IDs and related data from osteo1
        ParticipantDto participantDto = new ParticipantDto.Builder(osteo2InstanceId, System.currentTimeMillis())
                .withDdpParticipantId(osteo1Participant.getDdpParticipantIdOrThrow())
                .withLastVersion(osteo1Participant.getLastVersion().orElse(null))
                .withLastVersionDate(osteo1Participant.getLastVersionDate().orElse(null))
                .withReleaseCompleted(osteo1Participant.getReleaseCompleted().orElse(null))
                .withChangedBy(SystemUtil.SYSTEM).build();
        return participantDao.create(participantDto);
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
        return recs.size() == 1 && recs.get(0).getDdpInstanceId() == osteo1Instance.getDdpInstanceIdAsInt();
    }

    public boolean isOsteoInstance(DDPInstance ddpInstance) {
        return osteoInstanceNames.contains(ddpInstance.getName());
    }
}
