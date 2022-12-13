package org.broadinstitute.ddp.model.event;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.KitCheckService;
import org.jdbi.v3.core.Handle;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;

@Slf4j
public class CreateKitEventAction extends EventAction {

    private Long kitTypeId;
    /**
     * Key is study guid, value is the metrics transmitter for the study
     */
    private static final Map<String, StackdriverMetricsTracker> kitCounterMonitorByStudy = new HashMap<>();

    public CreateKitEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        kitTypeId = dto.getKitTypeId();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal signal) {
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting != null && delayBeforePosting > 0) {
            if (!eventConfiguration.dispatchToHousekeeping()) {
                throw new DDPException("Incompatible event configuration:"
                        + " delayed Kit Creation events should set dispatchToHousekeeping");
            }
            long queuedEventId = queueDelayedEvent(handle, signal);
            log.info("Queued Create Kit event with id {}", queuedEventId);
        } else {
            doActionSynchronously(handle, signal);
        }
    }

    public void doActionSynchronously(Handle handle, EventSignal signal) {

        List<KitConfigurationDto> kitConfigs = handle.attach(KitConfigurationDao.class)
                .getKitConfigurationDtosByStudyId(signal.getStudyId());

        //load user and address
        DsmAddressValidationStatus statusType = null;
        User user = handle.attach(UserDao.class).findUserByGuid(signal.getParticipantGuid()).get();
        MailAddress defaultAddress = handle.attach(JdbiMailAddress.class)
                .findDefaultAddressForParticipant(signal.getParticipantGuid())
                .orElse(null);

        if (defaultAddress == null) {
            log.error("Participant {} is missing a default mailing address. Deleting the create kit event", signal.getParticipantGuid());
            return;
        }

        if (defaultAddress.getValidationStatus() == DSM_INVALID_ADDRESS_STATUS.getCode()) {
            log.error("Participant {} has an invalid mailing address", signal.getParticipantGuid());
            return;
        } else {
            try {
                statusType = DsmAddressValidationStatus.getByCode(defaultAddress.getValidationStatus());
            } catch (Exception e) {
                log.error(e.getMessage() + ". Participant: {} ", signal.getParticipantGuid());
                return;
            }
        }

        KitConfiguration kitConfig = kitConfigs.stream()
                .filter(dto -> dto.getKitTypeId() == kitTypeId)
                .findFirst()
                .map(dto -> handle.attach(KitConfigurationDao.class).getKitConfigurationForDto(dto))
                .orElse(null);

        KitCheckService service = new KitCheckService();
        KitCheckService.PotentialRecipient candidate = new KitCheckService.PotentialRecipient(user.getId(),
                user.getGuid(), defaultAddress.getId(), statusType);
        KitCheckService.KitCheckResult kitCheckResult = new KitCheckService.KitCheckResult();
        kitCheckResult = service.processPotentialKitRecipient(signal.getStudyGuid(), kitTypeId,
                kitCheckResult, kitConfig, candidate, true);

        //send metric for the study
        if (kitCheckResult != null) {
            sendKitMetrics(signal.getStudyGuid(), kitCheckResult);
            log.info("Queued {} participants for Kit creation", kitCheckResult.getTotalNumberOfParticipantsQueuedForKit());
        }
    }

    @VisibleForTesting
    long queueDelayedEvent(Handle handle, EventSignal signal) {
        return handle.attach(QueuedEventDao.class).addToQueue(
                eventConfiguration.getEventConfigurationId(),
                signal.getOperatorId(),
                signal.getParticipantId(),
                eventConfiguration.getPostDelaySeconds());
    }

    private void sendKitMetrics(String studyGuid, KitCheckService.KitCheckResult kitCheckResult) {
        StackdriverMetricsTracker tracker = kitCounterMonitorByStudy.computeIfAbsent(studyGuid, key ->
                new StackdriverMetricsTracker(StackdriverCustomMetric.KITS_REQUESTED, studyGuid,
                        PointsReducerFactory.buildSumReducer()));
        tracker.addPoint(kitCheckResult.getTotalNumberOfParticipantsQueuedForKit(), Instant.now().toEpochMilli());
    }

}
