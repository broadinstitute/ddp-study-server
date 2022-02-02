package org.broadinstitute.ddp.event.pubsubtask.impl.userdelete;

import static org.broadinstitute.ddp.db.TransactionWrapper.DB.APIS;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__OPERATOR_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.impl.userdelete.UserDeleteConstants.FIELD__COMMENT;

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
 * Other parameters to be specified:<br>
 * - attribute "operatorGuid" (optional);<br>
 * - payload property "comment" (optional).
 */
public class UserDeleteProcessor extends PubSubTaskProcessorAbstract {

    private static final String PROP_NAME__OPERATOR_GUID = "operatorGuid=";

    @Override
    public void handleTask(PubSubTask pubSubTask) {
        TransactionWrapper.useTxn(APIS, handle -> deleteUser(handle, pubSubTask, createUserDeleteService()));
    }

    @Override
    protected void validateTaskData(PubSubTask pubSubTask) {
        super.validateTaskData(pubSubTask);
        if (participantGuid == null) {
            throwIfInvalidAttribute(pubSubTask, ATTR_NAME__PARTICIPANT_GUID, participantGuid);
        }
    }

    @Override
    protected boolean isEmptyPayloadAllowed() {
        return true;
    }

    private void deleteUser(Handle handle, PubSubTask pubSubTask, UserDeleteService userDeleteService) {
        try {
            User user = UserDeleteService.getUser(handle, participantGuid);
            userDeleteService.fullDelete(handle, user,
                    PROP_NAME__OPERATOR_GUID + pubSubTask.getAttributes().get(ATTR_NAME__OPERATOR_GUID),
                    payloadProps.getProperty(FIELD__COMMENT));
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
