package org.broadinstitute.ddp.event.publish.pubsub;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * PubSub utility methods
 */
@Slf4j
public class PubSubUtil {
    /**
     * Publish a specified pubSubMessage to a specified PubSub publisher (assigned to a specified topic)
     * @param publisher PubSub Publisher where to publish a message
     * @param pubSubMessage PubSub message which to publish
     * @param logMessage log message to display in log records
     */
    public static void publishMessage(Publisher publisher, PubsubMessage pubSubMessage, String logMessage) {
        log.info("Publish " + logMessage);

        ApiFuture<String> publishResult = publisher.publish(pubSubMessage);

        ApiFutures.addCallback(

                publishResult,

                new ApiFutureCallback<>() {

                    @Override
                    public void onFailure(Throwable e) {
                        String statusCode = null;
                        if (e instanceof ApiException) {
                            statusCode = ((ApiException) e).getStatusCode().getCode().toString();
                        }
                        String msg = "Failed to publish " + logMessage;
                        if (statusCode != null) {
                            msg += ", statusCode=" + statusCode;
                        }
                        log.error(msg, e);
                    }

                    @Override
                    public void onSuccess(String messageId) {
                        log.info("Successfully published " + logMessage);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }
}
