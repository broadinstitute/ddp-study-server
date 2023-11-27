package org.broadinstitute.dsm.pubsub;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderUseCase;
import org.broadinstitute.dsm.exception.DSMPubSubException;
import org.broadinstitute.dsm.model.mercury.MercuryPdoOrder;
import org.broadinstitute.dsm.model.mercury.MercuryPdoOrderBase;
import org.broadinstitute.dsm.util.NanoIdUtil;

@Slf4j
public class MercuryOrderPublisher {
    private final MercuryOrderDao mercuryOrderDao;
    private final ParticipantDao participantDao;

    public MercuryOrderPublisher(MercuryOrderDao mercuryOrderDao,
                                 ParticipantDao participantDao) {
        this.mercuryOrderDao = mercuryOrderDao;
        this.participantDao = participantDao;
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
                            throw new RuntimeException("Error publishing message " + topicId, throwable);
                        }

                        @Override
                        public void onSuccess(String messageId) {
                            // Once published, returns server-assigned message ids (unique within the topic)
                            log.info("Published to mercury message ID: " + messageId);
                            log.info("Message published to Mercury:" + messageData);
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

    private String createMercuryUniqueOrderId() {
        String orderNumber = NanoIdUtil.getNanoId("1234567890QWERTYUIOPASDFGHJKLZXCVBNM", 20);
        while (this.mercuryOrderDao.orderNumberExists(orderNumber)) {
            orderNumber = NanoIdUtil.getNanoId("1234567890QWERTYUIOPASDFGHJKLZXCVBNM", 20);
        }
        return orderNumber;

    }

    public String createAndPublishMessage(String[] barcodes, String projectId, String topicId, DDPInstanceDto ddpInstance,
                                          String collaboratorParticipantId, String userId, String ddpParticipantId) {
        if (StringUtils.isBlank(collaboratorParticipantId) && StringUtils.isBlank(ddpParticipantId)) {
            throw new RuntimeException("Invalid input!");
        }
        if (StringUtils.isBlank(ddpParticipantId)) {
            Optional<String> maybeParticipantId =
                    participantDao.getParticipantFromCollaboratorParticipantId(collaboratorParticipantId,
                            String.valueOf(ddpInstance.getDdpInstanceId()));
            ddpParticipantId = maybeParticipantId.orElseThrow();
        }
        log.info("Publishing message to mercury");
        String researchProject = ddpInstance.getResearchProject().orElseThrow();
        String creatorId = ddpInstance.getMercuryOrderCreator().orElseThrow();
        String mercuryOrderId = createMercuryUniqueOrderId();
        MercuryPdoOrder mercuryPdoOrder = new MercuryPdoOrder(creatorId, mercuryOrderId, researchProject, barcodes);
        MercuryPdoOrderBase mercuryPdoOrderBase = new MercuryPdoOrderBase(mercuryPdoOrder);
        String json = new Gson().toJson(mercuryPdoOrderBase);
        if (StringUtils.isNotBlank(json)) {
            try {
                List<MercuryOrderDto> newOrders = MercuryOrderUseCase.createAllOrders(barcodes, ddpParticipantId, mercuryOrderId, userId);
                publishWithErrorHandler(projectId, topicId, json);
                this.mercuryOrderDao.insertMercuryOrders(newOrders, json);
                MercuryOrderUseCase.exportToES(newOrders);
                return mercuryOrderId;
            } catch (Exception e) {
                throw new RuntimeException("Unable to  publish to pubsub/ db " + json, e);
            }

        }
        log.warn("json was empty for this order");
        return null;
    }


}
