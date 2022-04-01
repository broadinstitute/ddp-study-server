package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds {@link PubSubTaskResult}-message and publishes it
 * to outgoing topic (name specified in Config parameter
 * "pubsub.pubSubTasksResultTopic").
 */
@Slf4j
public class PubSubTaskResultSender implements ResultSender {
    private final Publisher publisher;

    public PubSubTaskResultSender(Publisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void sendPubSubTaskResult(PubSubTaskResult pubSubTaskResult) {

        PubsubMessage pubSubMessage = createPubSubMessage(pubSubTaskResult);

        log.info(format(infoMsg("Publish PubSubTaskResult message to topic=%s: result={%s}, pubSubTask={%s}"),
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
                        log.error(msg, e);
                    }

                    @Override
                    public void onSuccess(String messageId) {
                        log.info(infoMsg("Result \"{}\" successfully published to pubsub topic={}. MessageId={}"),
                                pubSubTaskResult, publisher.getTopicName(), messageId);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    public static PubsubMessage createPubSubMessage(PubSubTaskResult pubSubTaskResult) {
        var messageBuilder = PubsubMessage.newBuilder();
        if (pubSubTaskResult.getPayloadJson() != null) {
            messageBuilder.setData(ByteString.copyFromUtf8(pubSubTaskResult.getPayloadJson()));
        }
        messageBuilder.putAllAttributes(pubSubTaskResult.getAttributes());
        return messageBuilder.build();
    }
}
