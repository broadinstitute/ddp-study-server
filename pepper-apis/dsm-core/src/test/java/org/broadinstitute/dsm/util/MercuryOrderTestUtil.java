package org.broadinstitute.dsm.util;

import java.util.HashSet;
import java.util.Set;

import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;


public class MercuryOrderTestUtil {

    private final MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();

    private final Set<Integer> createdOrderIds = new HashSet<>();

    public MercuryOrderDto createMercuryOrder(String ddpParticipantId, String orderBarcode,
                                              int kitTypeId, int ddpInstanceId, int tissueId) {
        MercuryOrderDto orderDto = new MercuryOrderDto(ddpParticipantId, ddpParticipantId, orderBarcode,
                kitTypeId, ddpInstanceId, tissueId, null);
        orderDto.setOrderId(orderBarcode);
        int createdOrderId = mercuryOrderDao.create(orderDto, null);
        orderDto.setMercurySequencingId(createdOrderId);
        createdOrderIds.add(orderDto.getMercurySequencingId());
        return orderDto;
    }

    public void deleteCreatedOrders() {
        for (int createdOrderId : createdOrderIds) {
            mercuryOrderDao.delete(createdOrderId);
        }
    }
}
