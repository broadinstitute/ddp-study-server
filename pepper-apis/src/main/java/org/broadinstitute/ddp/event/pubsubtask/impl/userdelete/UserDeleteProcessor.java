package org.broadinstitute.ddp.event.pubsubtask.impl.userdelete;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.broadinstitute.ddp.db.TransactionWrapper.DB.APIS;
import static org.broadinstitute.ddp.event.pubsubtask.impl.userdelete.UserDeleteConstants.FIELD__COMMENT;
import static org.broadinstitute.ddp.event.pubsubtask.impl.userdelete.UserDeleteConstants.FIELD__WHO_DELETED;

import java.net.MalformedURLException;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskProcessorAbstract;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.service.UserDeleteService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.jdbi.v3.core.Handle;


/**
 * Processes PubSubTask of taskType='USER_DELETE'.
 * It deletes a specified user (GUID should be specified in {@link PubSubTask} attribute "participantGuid").<br>
 * The task
 *
 */
public class UserDeleteProcessor extends PubSubTaskProcessorAbstract {


    @Override
    public void handleTask(PubSubTask pubSubTask) {
        TransactionWrapper.useTxn(APIS, handle -> deleteUser(handle, createUserDeleteService()));
    }

    @Override
    protected void validateTaskData(PubSubTask pubSubTask) {
        super.validateTaskData(pubSubTask);
        if (participantGuid == null) {
            throwIfInvalidAttribute(pubSubTask, PubSubTask.ATTR_NAME__PARTICIPANT_GUID, participantGuid);
        }
        String whoDeleted = payloadProps.getProperty(FIELD__WHO_DELETED);
        if (isBlank(whoDeleted)) {
            throwIfInvalidPayloadProperty(pubSubTask, FIELD__WHO_DELETED, whoDeleted);
        }
    }

    @Override
    protected boolean isEmptyPayloadAllowed() {
        return true;
    }

    private void deleteUser(Handle handle, UserDeleteService userDeleteService) {
        try {
            User user = UserDeleteService.getUser(handle, participantGuid);
            userDeleteService.fullDelete(handle, user,
                    payloadProps.getProperty(FIELD__WHO_DELETED), payloadProps.getProperty(FIELD__COMMENT));
        } catch (Exception e) {
            throw new PubSubTaskException("Error delete user " + participantGuid, e);
        }
    }

    private UserDeleteService createUserDeleteService() {
        try {
            Config cfg = ConfigManager.getInstance().getConfig();
            RestHighLevelClient esClient = ElasticsearchServiceUtil.getElasticsearchClient(cfg);
            return new UserDeleteService(esClient);
        } catch (MalformedURLException e) {
            throw new DDPException("Error initialize ElasticSearch client:" + e.getMessage());
        }
    }
}
