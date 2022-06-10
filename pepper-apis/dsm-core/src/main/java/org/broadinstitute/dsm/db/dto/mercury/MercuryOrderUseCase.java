package org.broadinstitute.dsm.db.dto.mercury;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;

public class MercuryOrderUseCase {
    public static List<MercuryOrderDto> createAllOrders(String[] barcodes, String ddpParticipantId, String orderId, String userId)
            throws NoSuchElementException {
        List<MercuryOrderDto> orders = new ArrayList<>();
        MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();
        HashMap<String, MercuryOrderDto> participantBarcodes = mercuryOrderDao.getPossibleBarcodesForParticipant(ddpParticipantId);
        Arrays.stream(barcodes).forEach(barcode -> {
            if (!participantBarcodes.containsKey(barcode)) {
                String message = String.format(
                        "Cannot find barcode %s belonging to participant %s",
                        barcode, ddpParticipantId);
                throw new NoSuchElementException(message);
            }
            MercuryOrderDto order = participantBarcodes.get(barcode);
            order.setOrderId(orderId);
            order.setOrderDate(System.currentTimeMillis());
            order.setCreatedBy(userId);
            orders.add(order);
        });
        return orders;
    }
}
