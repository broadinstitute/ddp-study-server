package org.broadinstitute.ddp.db.housekeeping.dao;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.ddp.client.DsmClient;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitScheduleDao;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitSchedule;
import org.broadinstitute.ddp.model.kit.KitScheduleRecord;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.EventService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.EnumMapper;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitCheckDao {

    private static final Logger LOG = LoggerFactory.getLogger(KitCheckDao.class);

    private final PexInterpreter interpreter;

    public KitCheckDao() {
        interpreter = new TreeWalkInterpreter();
    }

    /**
     * Get all kit configurations across studies and see if there's participants who need a kit request queued up.
     *
     * <p>A participant must meet minimum requirements in order to receive a kit:
     * <ul>
     *     <li>is enrolled in study</li>
     *     <li>have a valid mailing address</li>
     * </ul>
     *
     * <p>Beyond these minimum requirements, the kit configuration also specifies additional criteria that are evaluated
     * before a participant qualifies for a kit request.
     *
     * @param apisHandle the apis database handle
     * @return a mapping between each study and the number of participants who had a kit queued in this iteration.  The mapping is
     * <b>not</b> the total number of participants with kits.  It's just the number of newly queued participants.
     */
    public KitCheckResult checkForKits(Handle apisHandle) {
        KitCheckResult kitCheckResult = new KitCheckResult();

        KitScheduleDao kitScheduleDao = apisHandle.attach(KitScheduleDao.class);
        DsmKitRequestDao kitRequestDao = apisHandle.attach(DsmKitRequestDao.class);
        KitConfigurationDao kitConfigurationDao = apisHandle.attach(KitConfigurationDao.class);

        for (KitConfiguration kitConfiguration : kitConfigurationDao.kitConfigurationFactory()) {
            long kitTypeId = kitConfiguration.getKitType().getId();
            String studyGuid = kitConfiguration.getStudyGuid();

            int numKits = kitConfiguration.getNumKits();
            if (numKits <= 0) {
                LOG.warn("Kit configuration with id {} has no kit quantity configured, skipping", kitConfiguration.getId());
                continue;
            }

            findPotentialKitRecipients(apisHandle, studyGuid, kitTypeId).forEach(candidate -> {
                String userGuid = candidate.getUserGuid();

                if (candidate.getAddressId() == null) {
                    LOG.warn("Participant {} is missing a default mailing address", userGuid);
                    return;
                }

                if (candidate.getAddressValidationStatus() == null
                        || candidate.getAddressValidationStatus() == DSM_INVALID_ADDRESS_STATUS) {
                    LOG.warn("Participant {} has an invalid mailing address", userGuid);
                    return;
                }

                boolean success = kitConfiguration.evaluate(apisHandle, userGuid);

                if (success) {
                    Long kitRequestId = null;
                    for (int i = 0; i < numKits; i++) {
                        LOG.info("Creating kit request for {}", userGuid);
                        kitRequestId = kitRequestDao.createKitRequest(studyGuid, candidate.getUserId(),
                                candidate.getAddressId(), kitTypeId, kitConfiguration.needsApproval());
                        LOG.info("Created kit request id {} for {}. Completed {} out of {} kits",
                                kitRequestId, userGuid, i + 1, numKits);
                    }
                    if (kitConfiguration.getSchedule() != null) {
                        // Add a tracking record for participant if kit has a reoccurring schedule.
                        long id = kitScheduleDao.createInitialScheduleRecord(candidate.getUserId(), kitConfiguration.getId(), kitRequestId);
                        LOG.info("Added kit schedule record with id={} for tracking reoccurring kits"
                                + " for participantGuid={} and kitConfigurationId={}", id, userGuid, kitConfiguration.getId());
                    }
                    kitCheckResult.incrementQueuedParticipantCountForStudy(studyGuid);
                } else {
                    LOG.warn("Participant {} was ineligible for a kit", userGuid);
                }
            });
        }

        return kitCheckResult;
    }

    /**
     * Go through all kit configurations and all pending participants and queue up reoccurring kits (e.g. kits after the initial kit).
     *
     * @param apisHandle the database handle
     * @return mapping of study to number of participants queued in this run
     */
    public KitCheckResult scheduleKits(Handle apisHandle) {
        var configs = apisHandle.attach(KitConfigurationDao.class)
                .kitConfigurationFactory()
                .stream()
                .filter(config -> config.getSchedule() != null)
                .filter(config -> config.getNumKits() >= 0)
                .collect(Collectors.toList());

        var dsmClient = new DsmClient(ConfigManager.getInstance().getConfig());
        var kitScheduleDao = apisHandle.attach(KitScheduleDao.class);

        var kitCheckResult = new KitCheckResult();
        for (var config : configs) {
            KitSchedule schedule = config.getSchedule();
            var pendingRecords = kitScheduleDao
                    .findPendingScheduleRecords(config.getId(), schedule.getNumOccurrencesPerUser())
                    .filter(pending -> !pending.getRecord().hasOptedOut())
                    .collect(Collectors.toList());
            for (var pending : pendingRecords) {
                scheduleKitsForParticipant(apisHandle, dsmClient, kitCheckResult, config, pending);
            }
        }

        return kitCheckResult;
    }

    private void scheduleKitsForParticipant(Handle apisHandle, DsmClient dsmClient, KitCheckResult kitCheckResult,
                                            KitConfiguration config, KitScheduleDao.PendingScheduleRecord pending) {
        KitSchedule schedule = config.getSchedule();
        KitScheduleRecord record = pending.getRecord();
        String studyGuid = config.getStudyGuid();
        String userGuid = pending.getUserGuid();

        Instant lastTime = determineLastTimePoint(apisHandle, dsmClient, studyGuid, userGuid, record);
        if (lastTime == null) {
            return;
        }

        Instant nextPrepTime = schedule.getNextPrepTimePoint(lastTime);
        Instant nextTime = schedule.getNextTimePoint(lastTime);

        if (nextPrepTime != null && nextPrepTime.isBefore(Instant.now()) && record.getCurrentOccurrencePrepTime() == null) {
            // Schedule has a prep step, and it's time for it, and we haven't done it yet for this occurrence.
            boolean shouldSkip = handlePrepStep(apisHandle, pending, schedule, record);
            if (shouldSkip) {
                return;
            }
        }

        if (nextTime.isBefore(Instant.now())) {
            // Time is up for the next kit!
            handleNextKit(apisHandle, kitCheckResult, config, pending);
        }
    }

    private Instant determineLastTimePoint(Handle apisHandle, DsmClient dsmClient, String studyGuid,
                                           String userGuid, KitScheduleRecord record) {
        if (record.getLastKitRequestId() == null) {
            // Either this is first time or user opted-out of the last kit request, so use the occurrence time.
            return record.getLastOccurrenceTime();
        } else if (record.getLastKitSentTime() != null) {
            // User got a kit last time and we already know when it was shipped, so use it.
            return record.getLastKitSentTime();
        } else {
            // User got a kit but we haven't found out when it was sent. Let's try asking DSM about it.
            var result = dsmClient.getParticipantStatus(studyGuid, userGuid, null);
            if (result.hasThrown() || result.getStatusCode() != 200) {
                // Something wrong, so skip this participant and move on.
                LOG.error("Error while getting participant kit status from DSM, statusCode={}, participantGuid={}",
                        result.getStatusCode(), userGuid, result.getThrown());
                return null;
            }
            // Our kit request guid is their kit id.
            Instant sent = result.getBody().getSamples().stream()
                    .filter(kit -> record.getLastKitRequestGuid().equals(kit.getKitRequestId()))
                    .findFirst()
                    .map(kit -> Instant.ofEpochSecond(kit.getSentEpochTimeSec()))
                    .orElse(null);
            if (sent != null) {
                // Save the sent time so we can cache it and not have to call DSM again.
                apisHandle.attach(KitScheduleDao.class).updateRecordLastKitSentTime(record.getId(), sent);
                return sent;
            } else {
                // Last kit was not sent out yet, no point in doing anything more for this participant.
                return null;
            }
        }
    }

    private boolean handlePrepStep(Handle apisHandle, KitScheduleDao.PendingScheduleRecord pending,
                                   KitSchedule schedule, KitScheduleRecord record) {
        KitScheduleDao kitScheduleDao = apisHandle.attach(KitScheduleDao.class);
        if (schedule.getOptOutExpr() != null && record.getNumOccurrences() == 0) {
            // This is the first occurrence and schedule allows opt-out. Let's see if we should apply it.
            try {
                boolean shouldOptOut = interpreter.eval(schedule.getOptOutExpr(), apisHandle, pending.getUserGuid(), null);
                if (shouldOptOut) {
                    // They're opting out, save that and move on.
                    kitScheduleDao.updateRecordOptOut(record.getId(), true);
                    return true;
                }
            } catch (Exception e) {
                // Somehow there's an error, so skip over this one.
                LOG.error("Error while determining if participant should opt-out of entire kit schedule,"
                        + " participantGuid={}, kitConfigurationId={}", pending.getUserGuid(), schedule.getConfigId(), e);
                return true;
            }
        }
        // Haven't opted-out yet, let's run prep step.
        var signal = new EventSignal(
                pending.getUserId(),
                pending.getUserId(),
                pending.getUserGuid(),
                pending.getStudyId(),
                EventTriggerType.KIT_PREP);
        EventService.getInstance().processAllActionsForEventSignal(apisHandle, signal);
        kitScheduleDao.updateRecordCurrentOccurrencePrepTime(record.getId(), Instant.now());
        return false;
    }

    private void handleNextKit(Handle apisHandle, KitCheckResult kitCheckResult,
                               KitConfiguration config, KitScheduleDao.PendingScheduleRecord pending) {
        var kitScheduleDao = apisHandle.attach(KitScheduleDao.class);
        var kitRequestDao = apisHandle.attach(DsmKitRequestDao.class);
        KitSchedule schedule = config.getSchedule();
        KitScheduleRecord record = pending.getRecord();
        String studyGuid = config.getStudyGuid();
        String userGuid = pending.getUserGuid();

        if (schedule.getOptOutExpr() != null && schedule.getNextPrepTimeAmount() == null && record.getNumOccurrences() == 0) {
            // This is the first occurrence, and schedule allows opt-out, and they haven't been given the opportunity
            // to opt-out yet since there's no prep step. Let's check it now.
            try {
                boolean shouldOptOut = interpreter.eval(schedule.getOptOutExpr(), apisHandle, userGuid, null);
                if (shouldOptOut) {
                    // They're opting out, save that and move on.
                    kitScheduleDao.updateRecordOptOut(record.getId(), true);
                    return;
                }
            } catch (Exception e) {
                // Somehow there's an error, so skip over this one.
                LOG.error("Error while determining if participant should opt-out of entire kit schedule,"
                        + " participantGuid={}, kitConfigurationId={}", userGuid, schedule.getConfigId(), e);
                return;
            }
        }

        if (schedule.getIndividualOptOutExpr() != null) {
            // Schedule allows opting out of individual kits, let's check it.
            try {
                boolean shouldOptOut = interpreter.eval(schedule.getIndividualOptOutExpr(), apisHandle, userGuid, null);
                if (shouldOptOut) {
                    // They're opting out, bump up the occurrence and move on.
                    kitScheduleDao.incrementRecordNumOccurrenceWithoutKit(record.getId());
                    return;
                }
            } catch (Exception e) {
                // Somehow there's an error, so skip over this one.
                LOG.error("Error while determining if participant should opt-out of kit for occurrence,"
                        + " participantGuid={}, kitConfigurationId={}", userGuid, config.getId(), e);
                return;
            }
        }

        // Haven't opted-out of this kit. Let's check the rules.
        if (pending.getAddressId() == null) {
            LOG.warn("Participant {} is missing a default mailing address", userGuid);
            return;
        }
        if (pending.getAddressValidationStatus() == null
                || pending.getAddressValidationStatus() == DSM_INVALID_ADDRESS_STATUS) {
            LOG.warn("Participant {} has an invalid mailing address", userGuid);
            return;
        }

        boolean success = config.evaluate(apisHandle, userGuid);
        if (success) {
            // All good. Create the next kit.
            Long kitRequestId = null;
            for (int i = 0; i < config.getNumKits(); i++) {
                LOG.info("Creating kit request for {}", userGuid);
                kitRequestId = kitRequestDao.createKitRequest(studyGuid, pending.getUserId(),
                        pending.getAddressId(), config.getKitType().getId());
                LOG.info("Created kit request id {} for {}. Completed {} out of {} kits",
                        kitRequestId, userGuid, i + 1, config.getNumKits());
            }
            kitScheduleDao.incrementRecordNumOccurrenceWithKit(record.getId(), kitRequestId);
            kitCheckResult.incrementQueuedParticipantCountForStudy(studyGuid);
        } else {
            LOG.warn("Participant {} was ineligible for next kit, kitConfigurationId={}", userGuid, config.getId());
        }
    }

    private Stream<PotentialRecipient> findPotentialKitRecipients(Handle apisHandle, String studyGuid, long kitTypeId) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(KitCheckDao.class, "queryAddressInfoForEnrolledUsersWithoutKits")
                .render();
        return apisHandle.createQuery(query)
                .bind("studyGuid", studyGuid)
                .bind("kitTypeId", kitTypeId)
                .registerRowMapper(ConstructorMapper.factory(PotentialRecipient.class))
                .registerColumnMapper(DsmAddressValidationStatus.class, EnumMapper.byOrdinal(DsmAddressValidationStatus.class))
                .mapTo(PotentialRecipient.class)
                .stream();
    }

    /**
     * Mapping between a study and the number of participants who have been queued for a kit
     */
    public static class KitCheckResult {

        private final Map<String, AtomicInteger> participantsQueuedForKitByStudy = new HashMap<>();

        public void incrementQueuedParticipantCountForStudy(String studyGuid) {
            if (!participantsQueuedForKitByStudy.containsKey(studyGuid)) {
                participantsQueuedForKitByStudy.put(studyGuid, new AtomicInteger(0));
            }
            participantsQueuedForKitByStudy.get(studyGuid).incrementAndGet();
        }

        public int getNumberOfParticipantsQueuedForKit(String studyGuid) {
            return participantsQueuedForKitByStudy.get(studyGuid).get();
        }

        /**
         * Returns a mapping between the study guid and the number of participants who had a kit queued in the study
         */
        public Set<Map.Entry<String, AtomicInteger>> getQueuedParticipantsByStudy() {
            return participantsQueuedForKitByStudy.entrySet();
        }

        /**
         * Returns the total number of participants who had a kit queued, irrespective of study
         */
        public int getTotalNumberOfParticipantsQueuedForKit() {
            int numQueued = 0;
            for (AtomicInteger numQueuedForStudy : participantsQueuedForKitByStudy.values()) {
                numQueued += numQueuedForStudy.get();
            }
            return numQueued;
        }
    }

    // Note: public so JDBI mapper can access this.
    public static class PotentialRecipient {
        private long userId;
        private String userGuid;
        private Long addressId;
        private DsmAddressValidationStatus addressValidationStatus;

        @ConstructorProperties({"user_id", "user_guid", "address_id", "address_validation_status"})
        public PotentialRecipient(long userId, String userGuid, Long addressId, DsmAddressValidationStatus addressValidationStatus) {
            this.userId = userId;
            this.userGuid = userGuid;
            this.addressId = addressId;
            this.addressValidationStatus = addressValidationStatus;
        }

        public long getUserId() {
            return userId;
        }

        public String getUserGuid() {
            return userGuid;
        }

        public Long getAddressId() {
            return addressId;
        }

        public DsmAddressValidationStatus getAddressValidationStatus() {
            return addressValidationStatus;
        }
    }
}
