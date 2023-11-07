package org.broadinstitute.dsm.util;

import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;

import java.util.HashSet;
import java.util.Set;

public class MercuryOrderTestUtil {

    private final MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();

    private final Set<Long> createdOrderIds = new HashSet<>();

    public MercuryOrderDto createMercuryOrder(String ddpParticipantId, String orderBarcode,
                                              int kitTypeId, int ddpInstanceId, int tissueId) {
        MercuryOrderDto orderDto = new MercuryOrderDto(ddpParticipantId, ddpParticipantId, orderBarcode,
                kitTypeId, ddpInstanceId, Integer.toUnsignedLong(tissueId), null);
        orderDto.setOrderId(orderBarcode);
        int createdOrderId = mercuryOrderDao.create(orderDto, null);
        orderDto.setMercurySequencingId(createdOrderId);
        createdOrderIds.add(orderDto.getMercurySequencingId());
        return orderDto;
    }

    public void deleteCreatedOrders() {
        for (Long createdOrderId : createdOrderIds) {
            mercuryOrderDao.delete(createdOrderId.intValue());
        }
    }
}
