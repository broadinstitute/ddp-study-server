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

@Slf4j
public class CreateKitEventAction extends EventAction {

    Long kitTypeId;
    /**
     * Key is study guid, value is the metrics transmitter for the study
     */
    private static final Map<String, StackdriverMetricsTracker> kitCounterMonitorByStudy = new HashMap<>();

    public CreateKitEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        kitTypeId = dto.getKitTypeId();
        if (kitTypeId == null) {
            //todo
            throw new DDPException("NO kitType in config");
        }
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
        if (kitConfigs == null || kitConfigs.isEmpty()) {
            return; //todo.. event.. no kitConfig
        }

        //load user and address
        User user = handle.attach(UserDao.class).findUserByGuid(signal.getParticipantGuid()).get();
        List<MailAddress> allAddresses = handle.attach(JdbiMailAddress.class)
                .findAllAddressesForParticipant(signal.getParticipantGuid());
        MailAddress address = null;
        DsmAddressValidationStatus statusType = null;
        Optional<MailAddress> defaultAddressOpt = allAddresses.stream().filter(mailAddress -> mailAddress.isDefault()).findFirst();
        if (!defaultAddressOpt.isPresent()) {
            log.warn("No default address for ptp : {} ", signal.getParticipantGuid());
            //no default mailing address ??
        } else {
            address = defaultAddressOpt.get();
            try {
                statusType = DsmAddressValidationStatus.getByCode(address.getValidationStatus());
            } catch (Exception e) {
                log.warn(e.getMessage() + ". Participant: {} ", signal.getParticipantGuid());
            }
        }

        Optional<KitConfigurationDto> kitConfigByTypeOpt = kitConfigs.stream().filter(dto -> dto.getKitTypeId()
                == kitTypeId).findFirst();

        KitConfiguration kitConfig = kitConfigByTypeOpt.isPresent()
                ? handle.attach(KitConfigurationDao.class).getKitConfigurationForDto(kitConfigByTypeOpt.get()) : null;

        KitCheckService service = new KitCheckService();
        KitCheckService.PotentialRecipient candidate = new KitCheckService.PotentialRecipient(user.getId(),
                user.getGuid(), address.getId(), statusType);
        KitCheckService.KitCheckResult kitCheckResult = new KitCheckService.KitCheckResult();
        kitCheckResult = service.processPotentialKitRecipient(signal.getParticipantGuid(), kitTypeId,
                kitCheckResult, kitConfig, candidate, true);

        //send metric by study
        if (kitCheckResult != null) {
            sendKitMetrics(kitCheckResult);
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

    private void sendKitMetrics(KitCheckService.KitCheckResult kitCheckResult) {
        for (var queuedParticipantsByStudy : kitCheckResult.getQueuedParticipantsByStudy()) {
            String studyGuid = queuedParticipantsByStudy.getKey();
            int numQueuedParticipants = queuedParticipantsByStudy.getValue().size();
            var tracker = kitCounterMonitorByStudy.computeIfAbsent(studyGuid, key ->
                    new StackdriverMetricsTracker(StackdriverCustomMetric.KITS_REQUESTED, studyGuid,
                            PointsReducerFactory.buildSumReducer()));
            tracker.addPoint(numQueuedParticipants, Instant.now().toEpochMilli());
        }
    }

}
