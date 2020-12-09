package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.studyemail.Attachment;
import org.broadinstitute.ddp.json.studyemail.SendStudyEmailPayload;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.service.FileUploadService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class SendStudyEmailRoute extends ValidatedJsonInputRoute<SendStudyEmailPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(SendStudyEmailRoute.class);

    private final FileUploadService fileUploadService;


    public SendStudyEmailRoute(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Override
    public Object handle(Request request, Response response, SendStudyEmailPayload payload) throws Exception {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);

        TransactionWrapper.useTxn(handle -> {
            Optional<Long> studyId = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);
            if (studyId.isEmpty()) {
                ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }
            LOG.info("Handling study email sending in study {}", studyGuid);
            List<EventConfigurationDto> eventConfigs =
                    handle.attach(EventDao.class)
                            .getNotificationConfigsForMailingListByEventType(studyGuid, EventTriggerType.SEND_STUDY_EMAIL);
            if (!eventConfigs.isEmpty()) {
                List<Attachment> attachments = payload.getAttachments();
                if (attachments != null) {
                    for (Attachment attachment : attachments) {
                        if (!fileUploadService.validateUpload(handle, attachment.getGuid())) {
                            String msg = String.format("Attachment %s is not verified", attachment.getGuid());
                            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, msg);
                        }
                    }
                }
                Config cfg = ConfigManager.getInstance().getConfig();
                String toEmail = cfg.getString(ConfigFile.Sendgrid.FROM_STUDY_EMAIL);
                for (EventConfigurationDto eventConfig : eventConfigs) {
                    Set<String> attachmentGuids = attachments != null
                            ? attachments.stream().map(Attachment::getGuid).collect(Collectors.toSet())
                            : null;
                    long queuedEventId = handle.attach(QueuedEventDao.class).insertNotification(eventConfig.getEventConfigurationId(),
                            0,
                            toEmail,
                            payload.getData(),
                            attachmentGuids);
                    LOG.info("Queued queuedEventId {} for study email sending.", queuedEventId);
                }
            }
        });

        // TODO
        return "";
    }
}
