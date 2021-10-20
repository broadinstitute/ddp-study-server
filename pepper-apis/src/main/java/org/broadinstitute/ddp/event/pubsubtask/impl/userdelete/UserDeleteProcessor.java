package org.broadinstitute.ddp.event.pubsubtask.impl.userdelete;

import java.net.MalformedURLException;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskProcessorAbstract;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.service.userdelete.UserFullDeleteService;
import org.broadinstitute.ddp.service.userdelete.UserService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.client.RestHighLevelClient;


/**
 * Processes PubSubTask of taskType='USER_DELETE'.
 * It deletes a specified user (GUID should be specified in {@link PubSubTask} attribute "participantGuid").<br>
 * The task
 *
 */
public class UserDeleteProcessor extends PubSubTaskProcessorAbstract {

    @Override
    public void handleTask(PubSubTask pubSubTask) {
        UserFullDeleteService userFullDeleteService = createUserDeleteService();
        try {
            userFullDeleteService.deleteUser(studyGuid, participantGuid);
        } catch (Exception e) {
            throw new PubSubTaskException("Error delete user " + participantGuid, e);
        }
    }

    @Override
    protected void validateTaskData(PubSubTask pubSubTask) {
        super.validateTaskData(pubSubTask);
        if (studyGuid == null) {
            throwIfInvalidAttribute(pubSubTask, PubSubTask.ATTR_NAME__STUDY_GUID, studyGuid);
        }
        if (participantGuid == null) {
            throwIfInvalidAttribute(pubSubTask, PubSubTask.ATTR_NAME__PARTICIPANT_GUID, participantGuid);
        }
    }

    @Override
    protected boolean isEmptyPayloadAllowed() {
        return true;
    }

    private UserFullDeleteService createUserDeleteService() {
        try {
            Config cfg = ConfigManager.getInstance().getConfig();
            RestHighLevelClient esClient = ElasticsearchServiceUtil.getElasticsearchClient(cfg);
            return new UserFullDeleteService(new UserService(esClient));
        } catch (MalformedURLException e) {
            throw new DDPException("Error initialize ElasticSearch client:" + e.getMessage());
        }
    }
}
