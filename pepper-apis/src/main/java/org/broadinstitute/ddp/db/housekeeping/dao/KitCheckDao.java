package org.broadinstitute.ddp.db.housekeeping.dao;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;

import java.beans.ConstructorProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.EnumMapper;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KitCheckDao {

    private static final Logger LOG = LoggerFactory.getLogger(KitCheckDao.class);

    public KitCheckDao() {}

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
         * Returns a mapping between the study guid and the number of participants
         * who had a kit queued in the study
         */
        public Set<Map.Entry<String, AtomicInteger>> getQueuedParticipantsByStudy() {
            return participantsQueuedForKitByStudy.entrySet();
        }

        /**
         * Returns the total number of participants who had a kit queued, irrespective
         * of study
         */
        public int getTotalNumberOfParticipantsQueuedForKit() {
            int numQueued = 0;
            for (AtomicInteger numQueuedForStudy : participantsQueuedForKitByStudy.values()) {
                numQueued += numQueuedForStudy.get();
            }
            return numQueued;
        }
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
                    for (int i = 0; i < numKits; i++) {
                        LOG.info("Creating kit request for {}", userGuid);
                        long kitRequestId = kitRequestDao.createKitRequest(studyGuid, candidate.getUserId(),
                                candidate.getAddressId(), kitTypeId);
                        LOG.info("Created kit request id {} for {}. Completed {} out of {} kits",
                                kitRequestId, userGuid, i + 1, numKits);
                    }
                    kitCheckResult.incrementQueuedParticipantCountForStudy(studyGuid);
                } else {
                    LOG.warn("Participant {} was ineligible for a kit", userGuid);
                }
            });
        }

        return kitCheckResult;
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
