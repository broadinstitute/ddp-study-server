package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import org.slf4j.Logger;

/**
 * Builds {@link PubSubTaskResult}-message and publishes it
 * to outgoing topic (name specified in Config parameter
 * "pubsub.pubSubTasksResultTopic").
 */
public class PubSubTaskResultSender implements ResultSender {

    private static final Logger LOG = getLogger(PubSubTaskResultSender.class);

    private final Publisher publisher;
    private final PubSubTaskResultMessageCreator messageCreator = new PubSubTaskResultMessageCreator();

    public PubSubTaskResultSender(Publisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void sendPubSubTaskResult(PubSubTaskResult pubSubTaskResult) {

        PubsubMessage pubSubMessage = messageCreator.createPubSubMessage(pubSubTaskResult);

        LOG.info(format(infoMsg("Publish PubSubTaskResult message to topic=%s: result={%s}, pubSubTask={%s}"),
                publisher.getTopicName(), pubSubTaskResult, pubSubTaskResult.getPubSubTask()));

        ApiFuture<String> publishResult = publisher.publish(pubSubMessage);

        ApiFutures.addCallback(
                publishResult,
                new ApiFutureCallback<>() {

                    @Override
                    public void onFailure(Throwable e) {
                        String statusCode = null;
                        if (e instanceof ApiException) {
                            ApiException apiException = ((ApiException) e);
                            statusCode = apiException.getStatusCode().getCode().toString();
                        }
                        String msg = errorMsg("Failed to send PubSubTask response \"" + pubSubTaskResult + "\"");
                        if (statusCode != null) {
                            msg += ", statusCode=" + statusCode;
                        }
                        LOG.error(msg, e);
                    }

                    @Override
                    public void onSuccess(String messageId) {
                        LOG.info(infoMsg("Result \"{}\" successfully published to pubsub topic={}. MessageId={}"),
                                pubSubTaskResult, publisher.getTopicName(), messageId);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }
}
