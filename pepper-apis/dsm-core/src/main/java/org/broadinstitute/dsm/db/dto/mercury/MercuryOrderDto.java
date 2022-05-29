package org.broadinstitute.dsm.db.dto.mercury;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import lombok.Data;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;

@Data

public class MercuryOrderDto {
    long mercurySequencingId;
    String ddpParticipantId;
    String orderId;
    String creatorId;
    String barcode;
    int kitTypeId;
    long orderDate;
    String orderStatus;
    long statusDate;
    String mercuryPdoId;

    public MercuryOrderDto(String ddpParticipantId, String creatorId, String barcode, int kitTypeId) {
        this.ddpParticipantId = ddpParticipantId;
        this.creatorId = creatorId;
        this.barcode = barcode;
        this.kitTypeId = kitTypeId;
    }

    public static List<MercuryOrderDto> createAllOrders(String[] barcodes, String ddpParticipantId) throws NoSuchElementException {
        List<MercuryOrderDto> orders = new ArrayList<>();
        MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();
        for (String barcode : barcodes) {
            Optional<MercuryOrderDto> maybeOrder = mercuryOrderDao.getMercuryOrderFromSMIdOrKitLabel(barcode, ddpParticipantId);
            maybeOrder.orElseThrow(() -> {
                String message = String.format(
                        "Cannot find barcode %s belonging to participant %s",
                        barcode, ddpParticipantId);
                return new NoSuchElementException(message);
            });
            maybeOrder.ifPresent(order -> orders.add(order));
        }
        return orders;
    }
}
