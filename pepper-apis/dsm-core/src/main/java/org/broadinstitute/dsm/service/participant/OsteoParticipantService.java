package org.broadinstitute.dsm.service.participant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.service.onchistory.OncHistoryService;
import org.broadinstitute.dsm.util.SystemUtil;
import spark.utils.StringUtils;


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
    public void setOsteoDefaultData(String ddpParticipantId, ElasticSearchParticipantDto participantDto,
                                    String payload) {
        // see if study name provided
        String studyName = null;
        if (!StringUtils.isEmpty(payload)) {
            try {
                JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();
                JsonElement nameObj = jsonObject.get("studyName");
                if (nameObj != null) {
                    studyName = nameObj.getAsString().trim();
                }
            } catch (Exception e) {
                throw new DSMBadRequestException(
                        "Error parsing payload for participant %s: %s".formatted(ddpParticipantId, payload), e);
            }
        }

        if (!StringUtils.isEmpty(studyName) && OSTEO1_INSTANCE_NAME.equalsIgnoreCase(studyName)) {
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
        createOsteoTag(dsm, ddpParticipantId, osteoInstance, tagName);
    }

    /**
     * Create a new cohort tag
     * @param dsm ES DSM data to update with new tag
     */
    private static void createOsteoTag(Dsm dsm, String ddpParticipantId, DDPInstance osteoInstance, String tagName) {
        log.info("Creating '{}' cohort tag for participant {} and instance {}",
                tagName, ddpParticipantId, osteoInstance.getName());
        CohortTag cohortTag = new CohortTag(tagName, ddpParticipantId, osteoInstance.getDdpInstanceIdAsInt());
        int newCohortTagId = cohortTagDao.create(cohortTag);
        cohortTag.setCohortTagId(newCohortTagId);

        updateEsCohortTags(dsm, ddpParticipantId, cohortTag, osteoInstance.getParticipantIndexES());
    }

    /**
     * Update ES with new cohort tag
     * @param dsm ES DSM data to update with new tag
     */
    protected static void updateEsCohortTags(Dsm dsm, String ddpParticipantId, CohortTag cohortTag, String esIndex) {
        List<CohortTag> cohortTags = dsm.getCohortTag();
        cohortTags.add(cohortTag);
        dsm.setCohortTag(cohortTags);

        ElasticSearchService.updateDsm(ddpParticipantId, dsm, esIndex);
    }

    /**
     * For osteo1 participant who consented to OS PE-CGS (OS2/osteo2), create a osteo2 cohort tag,
     * and copy osteo1 participant data to osteo2
     */
    public void initializeReconsentedParticipant(String ddpParticipantId) {
        log.info("Initializing re-consented osteo participant {}", ddpParticipantId);
        String osteo2Index = osteo2Instance.getParticipantIndexES();

        // copy osteo1 cohort tags to osteo2, and add an osteo2 tag to both osteo1 and osteo2
        Dsm osteo2Dsm = getParticipantDsm(ddpParticipantId, osteo2Index);
        copyCohortTags(osteo2Dsm, ddpParticipantId);
        createOsteoTag(osteo2Dsm, ddpParticipantId, osteo2Instance, OSTEO2_COHORT_TAG_NAME);

        Dsm osteo1Dsm = getParticipantDsm(ddpParticipantId, osteo1Instance.getParticipantIndexES());
        createOsteoTag(osteo1Dsm, ddpParticipantId, osteo1Instance, OSTEO2_COHORT_TAG_NAME);

        Optional<ParticipantDto> osteo1Ptp = participantDao
                .getParticipantForInstance(ddpParticipantId, osteo1Instance.getDdpInstanceIdAsInt());
        if (osteo1Ptp.isEmpty()) {
            log.info("Participant {} record not found for instance {}. No re-consent participant data to copy.",
                    ddpParticipantId, osteo1Instance.getName());
            return;
        }
        copyOsteo1Data(osteo1Ptp.get(), ddpParticipantId, osteo2Dsm);
    }

    /**
     * Get participant DSM ES data, or create it if missing
     */
    private Dsm getParticipantDsm(String ddpParticipantId, String esIndex) {
        // there *should* be ES DSM data for osteo2, but make it if not
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        Optional<Dsm> dsm = esParticipant.getDsm();
        return dsm.orElseGet(Dsm::new);
    }

    /**
     * Copy osteo1 medical record bundle to osteo2 for existing osteo1 participant
     */
    private void copyOsteo1Data(ParticipantDto osteo1Participant, String ddpParticipantId, Dsm osteo2Dsm) {
        log.info("Copying participant data for existing {} participant {} to {}",
                osteo1Instance.getName(), ddpParticipantId, osteo2Instance.getName());

        // ensure we can get what we need before copying and committing anything
        // there should already be ES DSM data in osteo1 index for this participant
        Dsm osteo1Dsm =
                elasticSearchService.getRequiredDsmData(ddpParticipantId, osteo1Instance.getParticipantIndexES());
        Participant esParticipant = osteo1Dsm.getParticipant().orElseThrow(() ->
                new DsmInternalError("ES Participant data missing for participant %s".formatted(ddpParticipantId)));

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

        osteo2Dsm.setMedicalRecord(copyMedicalRecords(osteo1Participant.getRequiredParticipantId(),
                osteo2ParticipantId, osteo2InstanceId));
        ElasticSearchService.updateDsm(ddpParticipantId, osteo2Dsm, osteo2Instance.getParticipantIndexES());
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
     * Copy cohort all tags from osteo1 to osteo2
     * @param dsm osteo2 ES DSM data to update with new tags
     */
    private void copyCohortTags(Dsm dsm, String ddpParticipantId) {
        List<CohortTag> cohortTags = cohortTagDao.getParticipantCohortTags(ddpParticipantId,
                osteo1Instance.getDdpInstanceIdAsInt());
        // Note: this may seem inefficient compared to a bulk process, but the likelihood of having
        // more than one tag is low, and this is a rare operation
        cohortTags.forEach(cohortTag ->
                createOsteoTag(dsm, ddpParticipantId, osteo2Instance, cohortTag.getCohortTagName()));
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
