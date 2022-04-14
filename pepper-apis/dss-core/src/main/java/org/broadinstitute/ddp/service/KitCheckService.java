package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitScheduleDao;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitSchedule;
import org.broadinstitute.ddp.model.kit.KitScheduleRecord;
import org.broadinstitute.ddp.model.kit.PendingScheduleRecord;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.mapper.EnumMapper;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;

@Slf4j
public class KitCheckService {
    private static final int DEFAULT_PENDING_BATCH_SIZE = 300;

    private final PexInterpreter interpreter;
    private final int batchSize;

    public KitCheckService() {
        this(DEFAULT_PENDING_BATCH_SIZE);
    }

    public KitCheckService(int batchSize) {
        interpreter = new TreeWalkInterpreter();
        this.batchSize = batchSize;
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
     * @return a mapping between each study and the number of participants who had a kit queued in this iteration. The
     *         mapping is <b>not</b> the total number of participants with kits. It's just the number of newly queued
     *         participants.
     */
    public KitCheckResult checkForInitialKits() {
        KitCheckResult kitCheckResult = new KitCheckResult();

        Queue<KitConfiguration> kitConfigs = withAPIsTxn(handle -> new ArrayDeque<>(
                handle.attach(KitConfigurationDao.class).kitConfigurationFactory()));

        while (!kitConfigs.isEmpty()) {
            KitConfiguration kitConfig = kitConfigs.remove();
            long kitConfigId = kitConfig.getId();
            long kitTypeId = kitConfig.getKitType().getId();
            String studyGuid = kitConfig.getStudyGuid();

            int numKits = kitConfig.getNumKits();
            if (numKits <= 0) {
                log.warn("Kit configuration with id {} has no kit quantity configured, skipping", kitConfigId);
                continue;
            }

            log.info("Processing potential kit recipients for study {} and kit configuration {}", studyGuid, kitConfigId);

            int fetched = 0;
            while (true) {
                int offset = fetched;
                Queue<PotentialRecipient> batch = withAPIsTxn(handle -> new ArrayDeque<>(
                        findPotentialKitRecipients(handle, studyGuid, kitTypeId, offset, batchSize)));
                int fetchedSize = batch.size();
                if (fetchedSize == 0) {
                    break;
                }
                while (!batch.isEmpty()) {
                    PotentialRecipient candidate = batch.remove();
                    try {
                        processPotentialKitRecipient(studyGuid, kitTypeId, kitCheckResult, kitConfig, candidate);
                    } catch (Exception e) {
                        log.error("Error while checking potential kit recipient {}, continuing", candidate.getUserGuid(), e);
                    }
                }
                fetched += fetchedSize;
            }

            log.info("Finished processing {} potential kit recipients for study {} and kit configuration {}",
                    fetched, studyGuid, kitConfigId);
        }

        return kitCheckResult;
    }

    private void processPotentialKitRecipient(String studyGuid, long kitTypeId,
                                              KitCheckResult kitCheckResult,
                                              KitConfiguration kitConfiguration,
                                              PotentialRecipient candidate) {
        String userGuid = candidate.getUserGuid();

        if (candidate.getAddressId() == null) {
            log.warn("Participant {} is missing a default mailing address", userGuid);
            return;
        }

        if (candidate.getAddressValidationStatus() == null
                || candidate.getAddressValidationStatus() == DSM_INVALID_ADDRESS_STATUS) {
            log.warn("Participant {} has an invalid mailing address", userGuid);
            return;
        }

        boolean wasSuccessful = withAPIsTxn(handle -> {
            boolean success = kitConfiguration.evaluate(handle, userGuid);

            KitScheduleDao kitScheduleDao = handle.attach(KitScheduleDao.class);
            DsmKitRequestDao kitRequestDao = handle.attach(DsmKitRequestDao.class);

            if (success) {
                int numKits = kitConfiguration.getNumKits();
                for (int i = 0; i < numKits; i++) {
                    log.info("Creating kit request for {}", userGuid);
                    Long kitRequestId = kitRequestDao.createKitRequest(studyGuid, candidate.getUserId(),
                            candidate.getAddressId(), kitTypeId, kitConfiguration.needsApproval());
                    log.info("Created kit request id {} for {}. Completed {} out of {} kits",
                            kitRequestId, userGuid, i + 1, numKits);
                }
                if (kitConfiguration.getSchedule() != null) {
                    // Add a tracking record for participant if kit has a reoccurring schedule.
                    long id = kitScheduleDao.createScheduleRecord(candidate.getUserId(), kitConfiguration.getId());
                    log.info("Added kit schedule record with id={} for tracking reoccurring kits"
                            + " for participantGuid={} and kitConfigurationId={}", id, userGuid, kitConfiguration.getId());
                }
            }

            return success;
        });

        if (wasSuccessful) {
            kitCheckResult.addQueuedParticipantForStudy(studyGuid, candidate.getUserId());
        } else {
            log.warn("Participant {} was ineligible for a kit", userGuid);
        }
    }

    /**
     * Go through all kit configurations and all pending participants and queue up reoccurring kits (e.g. kits after the
     * initial kit).
     *
     * @return mapping of study to number of participants queued in this run
     */
    public KitCheckResult scheduleNextKits() {
        Queue<KitConfiguration> kitConfigs = withAPIsTxn(handle -> new ArrayDeque<>(
                handle.attach(KitConfigurationDao.class)
                        .kitConfigurationFactory()
                        .stream()
                        .filter(config -> config.getSchedule() != null)
                        .filter(config -> config.getNumKits() >= 0)
                        .collect(Collectors.toList())));

        var kitCheckResult = new KitCheckResult();

        while (!kitConfigs.isEmpty()) {
            KitConfiguration kitConfig = kitConfigs.remove();
            long kitConfigId = kitConfig.getId();
            String studyGuid = kitConfig.getStudyGuid();
            log.info("Checking kit schedule records for study {} and kit configuration {}", studyGuid, kitConfigId);

            int fetched = 0;
            while (true) {
                int offset = fetched;
                Queue<PendingScheduleRecord> batch = withAPIsTxn(handle -> new ArrayDeque<>(
                        handle.attach(KitScheduleDao.class)
                                .findPendingScheduleRecords(kitConfig.getId(), offset, batchSize)));
                int fetchedSize = batch.size();
                if (fetchedSize == 0) {
                    break;
                }
                while (!batch.isEmpty()) {
                    PendingScheduleRecord record = batch.remove();
                    try {
                        scheduleNextKitForParticipant(kitCheckResult, kitConfig, record);
                    } catch (Exception e) {
                        log.error("Error while checking kit schedule record for participant {}, continuing", record.getUserGuid(), e);
                    }
                }
                fetched += fetchedSize;
            }

            log.info("Finished processing {} kit schedule records for study {} and kit configuration {}",
                    fetched, studyGuid, kitConfigId);
        }

        return kitCheckResult;
    }

    private void scheduleNextKitForParticipant(KitCheckResult kitCheckResult,
                                               KitConfiguration kitConfig,
                                               PendingScheduleRecord pending) {
        KitSchedule schedule = kitConfig.getSchedule();
        KitScheduleRecord record = pending.getRecord();

        Instant lastTime = record.determineLastTimePoint();
        if (lastTime != null) {
            Instant nextPrepTime = schedule.getNextPrepTimePoint(lastTime);
            Instant nextTime = schedule.getNextTimePoint(lastTime);

            if (nextPrepTime != null && record.shouldPerformPrepStep(nextPrepTime)) {
                boolean shouldSkip = withAPIsTxn(handle -> handlePrepStep(handle, pending, schedule, record));
                if (shouldSkip) {
                    return;
                }
            }

            if (nextTime.isBefore(Instant.now())) {
                // Time is up for the next kit!
                withAPIsTxn(handle -> {
                    handleNextKit(handle, kitCheckResult, kitConfig, pending);
                    return null;
                });
            }
        }
    }

    private boolean handlePrepStep(Handle apisHandle, PendingScheduleRecord pending,
                                   KitSchedule schedule, KitScheduleRecord record) {
        KitScheduleDao kitScheduleDao = apisHandle.attach(KitScheduleDao.class);
        if (schedule.getOptOutExpr() != null && record.getNumOccurrences() == 0) {
            // This is the first occurrence and schedule allows opt-out. Let's see if we should apply it.
            try {
                boolean shouldOptOut = interpreter.eval(schedule.getOptOutExpr(),
                        apisHandle, pending.getUserGuid(), pending.getUserGuid(), null);
                if (shouldOptOut) {
                    // They're opting out, save that and move on.
                    kitScheduleDao.updateRecordOptOut(record.getId(), true);
                    log.info("Participant {} is opting out of recurring kits for kit_configuration_id={}",
                            pending.getUserGuid(), schedule.getConfigId());
                    return true;
                }
            } catch (Exception e) {
                // Somehow there's an error, so skip over this one.
                log.error("Error while determining if participant should opt-out of entire kit schedule,"
                        + " participantGuid={}, kitConfigurationId={}", pending.getUserGuid(), schedule.getConfigId(), e);
                return true;
            }
        }
        // Haven't opted-out yet, let's run prep step.
        var signal = new EventSignal(
                pending.getUserId(),
                pending.getUserId(),
                pending.getUserGuid(),
                null,
                pending.getStudyId(),
                pending.getStudyGuid(),
                EventTriggerType.KIT_PREP);
        EventService.getInstance().processAllActionsForEventSignal(apisHandle, signal);
        kitScheduleDao.updateRecordCurrentOccurrencePrepTime(record.getId(), Instant.now());
        log.info("Preparation step finished for participant {} and occurrence {} of kit_configuration_id={}",
                pending.getUserGuid(), record.getNumOccurrences() + 1, schedule.getConfigId());
        return false;
    }

    private void handleNextKit(Handle apisHandle, KitCheckResult kitCheckResult,
                               KitConfiguration kitConfig, PendingScheduleRecord pending) {
        var kitScheduleDao = apisHandle.attach(KitScheduleDao.class);
        var kitRequestDao = apisHandle.attach(DsmKitRequestDao.class);
        KitSchedule schedule = kitConfig.getSchedule();
        KitScheduleRecord record = pending.getRecord();
        String studyGuid = kitConfig.getStudyGuid();
        String userGuid = pending.getUserGuid();

        if (schedule.getOptOutExpr() != null && schedule.getNextPrepTimeAmount() == null && record.getNumOccurrences() == 0) {
            // This is the first occurrence, and schedule allows opt-out, and they haven't been given the opportunity
            // to opt-out yet since there's no prep step. Let's check it now.
            try {
                boolean shouldOptOut = interpreter.eval(schedule.getOptOutExpr(), apisHandle, userGuid, null, null);
                if (shouldOptOut) {
                    // They're opting out, save that and move on.
                    kitScheduleDao.updateRecordOptOut(record.getId(), true);
                    log.info("Participant {} is opting out of recurring kits for kit_configuration_id={}",
                            pending.getUserGuid(), schedule.getConfigId());
                    return;
                }
            } catch (Exception e) {
                // Somehow there's an error, so skip over this one.
                log.error("Error while determining if participant should opt-out of entire kit schedule,"
                        + " participantGuid={}, kitConfigurationId={}", userGuid, schedule.getConfigId(), e);
                return;
            }
        }

        if (schedule.getIndividualOptOutExpr() != null) {
            // Schedule allows opting out of individual kits, let's check it.
            try {
                boolean shouldOptOut = interpreter.eval(schedule.getIndividualOptOutExpr(), apisHandle, userGuid, null, null);
                if (shouldOptOut) {
                    // They're opting out, bump up the occurrence and move on.
                    kitScheduleDao.incrementRecordNumOccurrence(record.getId());
                    log.info("Participant {} is opting out of kit for occurrence {} of kit_configuration_id={}",
                            pending.getUserGuid(), record.getNumOccurrences() + 1, schedule.getConfigId());
                    return;
                }
            } catch (Exception e) {
                // Somehow there's an error, so skip over this one.
                log.error("Error while determining if participant should opt-out of kit for occurrence,"
                        + " participantGuid={}, kitConfigurationId={}", userGuid, kitConfig.getId(), e);
                return;
            }
        }

        // Haven't opted-out of this kit. Let's check the rules.
        if (pending.getAddressId() == null) {
            log.warn("Participant {} is missing a default mailing address", userGuid);
            return;
        }
        if (pending.getAddressValidationStatus() == null
                || pending.getAddressValidationStatus() == DSM_INVALID_ADDRESS_STATUS) {
            log.warn("Participant {} has an invalid mailing address", userGuid);
            return;
        }

        boolean success = kitConfig.evaluate(apisHandle, userGuid);
        if (success) {
            // All good. Create the next kit.
            for (int i = 0; i < kitConfig.getNumKits(); i++) {
                log.info("Creating next kit request for {}", userGuid);
                long kitRequestId = kitRequestDao.createKitRequest(studyGuid, pending.getUserId(),
                        pending.getAddressId(), kitConfig.getKitType().getId(), kitConfig.needsApproval());
                log.info("Created next kit request id {} for {}. Completed {} out of {} kits",
                        kitRequestId, userGuid, i + 1, kitConfig.getNumKits());
            }
            kitScheduleDao.incrementRecordNumOccurrence(record.getId());
            kitCheckResult.addQueuedParticipantForStudy(studyGuid, pending.getUserId());
            log.info("Finished occurrence {} for participant {} and kit_configuration_id={}",
                    record.getNumOccurrences() + 1, pending.getUserGuid(), schedule.getConfigId());
        } else {
            log.warn("Participant {} was ineligible for next kit, kitConfigurationId={}", userGuid, kitConfig.getId());
        }
    }

    private List<PotentialRecipient> findPotentialKitRecipients(Handle apisHandle, String studyGuid, long kitTypeId,
                                                                int offset, int limit) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(KitCheckService.class, "queryAddressInfoForEnrolledUsersWithoutKits")
                .render();
        return apisHandle.createQuery(query)
                .bind("studyGuid", studyGuid)
                .bind("kitTypeId", kitTypeId)
                .bind("offset", offset)
                .bind("limit", limit)
                .registerRowMapper(ConstructorMapper.factory(PotentialRecipient.class))
                .registerColumnMapper(DsmAddressValidationStatus.class, EnumMapper.byOrdinal(DsmAddressValidationStatus.class))
                .mapTo(PotentialRecipient.class)
                .list();
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

    /**
     * Mapping between a study and the number of participants who have been queued for a kit
     */
    public static class KitCheckResult {

        private final Map<String, Set<Long>> participantsQueuedForKitByStudy = new HashMap<>();

        public void addQueuedParticipantForStudy(String studyGuid, long participantId) {
            participantsQueuedForKitByStudy
                    .computeIfAbsent(studyGuid, key -> new HashSet<>())
                    .add(participantId);
        }

        public int getNumberOfParticipantsQueuedForKit(String studyGuid) {
            return participantsQueuedForKitByStudy.getOrDefault(studyGuid, new HashSet<>()).size();
        }

        /**
         * Returns a mapping between the study guid and the participants who had a kit queued in the study
         */
        public Set<Map.Entry<String, Set<Long>>> getQueuedParticipantsByStudy() {
            return participantsQueuedForKitByStudy.entrySet();
        }

        /**
         * Returns the total number of participants who had a kit queued, irrespective of study
         */
        public int getTotalNumberOfParticipantsQueuedForKit() {
            int numQueued = 0;
            for (var queuedParticipantIds : participantsQueuedForKitByStudy.values()) {
                numQueued += queuedParticipantIds.size();
            }
            return numQueued;
        }

        public void add(KitCheckResult other) {
            for (var entry : other.getQueuedParticipantsByStudy()) {
                String studyGuid = entry.getKey();
                Set<Long> participants = entry.getValue();
                participantsQueuedForKitByStudy
                        .computeIfAbsent(studyGuid, key -> new HashSet<>())
                        .addAll(participants);
            }
        }
    }

    <R, X extends Exception> R withAPIsTxn(HandleCallback<R, X> callback) throws X {
        return TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, callback);
    }
}
