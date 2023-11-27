package org.broadinstitute.dsm.db.dto.mercury;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.model.mercury.ActionRequestMessage;
import org.broadinstitute.dsm.pubsub.MercuryOrderPublisher;

@Slf4j
public class ClinicalOrderUseCase {
    public void publishStatusActionMessage(ArrayList<ClinicalOrderDto> array, String projectId, String topicId) {
        for (ClinicalOrderDto clinicalOrderDto : array) {
            ActionRequestMessage actionRequestMessage = new ActionRequestMessage(clinicalOrderDto.getOrderId());
            String json = new Gson().toJson(actionRequestMessage);
            try {
                MercuryOrderPublisher.publishWithErrorHandler(projectId, topicId, json);
            } catch (InterruptedException | IOException e) {
                log.error("Error publishing an action request message for clinical order: " + clinicalOrderDto);
            }
        }
    }
}
