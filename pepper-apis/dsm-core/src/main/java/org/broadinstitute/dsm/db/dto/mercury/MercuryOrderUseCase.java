package org.broadinstitute.dsm.db.dto.mercury;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;

public class MercuryOrderUseCase {
    public static List<MercuryOrderDto> createAllOrders(String[] barcodes, String ddpParticipantId, String orderId)
            throws NoSuchElementException {
        List<MercuryOrderDto> orders = new ArrayList<>();
        MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();
        for (String barcode : barcodes) {
            Optional<MercuryOrderDto> maybeOrder = mercuryOrderDao.getMercuryOrderFromSMIdOrKitLabel(barcode, ddpParticipantId);
            MercuryOrderDto order = maybeOrder.orElseThrow(() -> {
                String message = String.format(
                        "Cannot find barcode %s belonging to participant %s",
                        barcode, ddpParticipantId);
                return new NoSuchElementException(message);
            });
            order.setOrderId(orderId);
            order.setOrderDate(System.currentTimeMillis());
            orders.add(order);
        }
        return orders;
    }
}
