package org.broadinstitute.dsm.pubsub;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.exception.DSMPubSubException;
import org.broadinstitute.dsm.model.mercury.MercuryPdoOrder;
import org.broadinstitute.dsm.util.NanoIdUtil;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class MercuryOrderPublisher {
    private static MercuryOrderDao mercuryOrderDao;

    public MercuryOrderPublisher(MercuryOrderDao mercuryOrderDao) {
        this.mercuryOrderDao = mercuryOrderDao;
    }

    private static String createPdoOrderJson(String[] barcodes, DDPInstance ddpInstance) {
        String researchProject = ddpInstance.getResearchProject();

        String creatorId = new InstanceSettingsDto.Builder()
                .withDdpInstanceId(Integer.parseInt(ddpInstance.getDdpInstanceId()))
                .build().getMercuryOrderCreator().orElseThrow();
        String mercuryOrderId = createMercuryUniqueOrderId();
        MercuryPdoOrder mercuryPdoOrder = new MercuryPdoOrder(creatorId, mercuryOrderId, researchProject, barcodes);
        String json = new Gson().toJson(mercuryPdoOrder);
        return json;

    }

    private static String createMercuryUniqueOrderId() {
        String orderNumber = NanoIdUtil.getNanoId("1234567890QWERTYUIOPASDFGHJKLZXCVBNM", 20);
        while (mercuryOrderDao.orderNumberExists(orderNumber)) {
            orderNumber = NanoIdUtil.getNanoId("1234567890QWERTYUIOPASDFGHJKLZXCVBNM", 20);
        }
        return orderNumber;

    }

    public static void publishWithErrorHandler(String projectId, String topicId, String messageData)
            throws IOException, InterruptedException {
        TopicName topicName = TopicName.of(projectId, topicId);
        Publisher publisher = null;

        try {
            // Create a publisher instance with default settings bound to the topic
            publisher = Publisher.newBuilder(topicName).build();

            ByteString data = ByteString.copyFromUtf8(messageData);
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(data).build();

            // Once published, returns a server-assigned message id (unique within the topic)
            ApiFuture<String> future = publisher.publish(pubsubMessage);

            // Add an asynchronous callback to handle success / failure
            ApiFutures.addCallback(
                    future,
                    new ApiFutureCallback<String>() {

                        @Override
                        public void onFailure(Throwable throwable) {
                            if (throwable instanceof ApiException) {
                                ApiException apiException = ((ApiException) throwable);
                                // details on the API exception
                                log.info(String.valueOf(apiException.getStatusCode().getCode()));
                                log.info(String.valueOf(apiException.isRetryable()));
                            }
                            log.error("Error publishing message " + topicId);
                        }

                        @Override
                        public void onSuccess(String messageId) {
                            // Once published, returns server-assigned message ids (unique within the topic)
                            log.info("Published message ID: " + messageId);
                        }
                    },
                    MoreExecutors.directExecutor()
            );
        } finally {
            if (publisher != null) {
                // When finished with the publisher, shutdown to free up resources.
                try {
                    publisher.shutdown();
                    publisher.awaitTermination(1, TimeUnit.MINUTES);
                } catch (Exception e) {
                    throw new DSMPubSubException("Error shutting down pubsub for Mercury subscription", e);
                }
            }
        }
    }

    public void publishMessage(String[] barcodes, String projectId, String topicId, DDPInstance ddpInstance, String ddpParticipantId) {
        log.info("Publishing message to mercury");
        String json = createPdoOrderJson(barcodes, ddpInstance);
        if (StringUtils.isNotBlank(json)) {
            orderOnMercury(projectId, topicId, json, barcodes, ddpParticipantId);
        }

    }

    private void orderOnMercury(String projectId, String topicId, String json, String[] barcodes,
                                String ddpParticipantId) {
        List<MercuryOrderDto> newOrders = MercuryOrderDto.createAllOrders(barcodes, ddpParticipantId);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult result = new SimpleResult();
            for (MercuryOrderDto order : newOrders) {
                result = this.mercuryOrderDao.create(order, conn);
                if (result.resultException != null) {
                    return result;
                }
            }
            publishWithErrorHandler(projectId, topicId, json);
            return result;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error inserting new order ", results.resultException);
        }
    }
}
