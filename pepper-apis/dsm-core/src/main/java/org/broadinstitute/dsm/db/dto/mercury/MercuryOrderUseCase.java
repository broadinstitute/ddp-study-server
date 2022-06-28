package org.broadinstitute.dsm.db.dto.mercury;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;

public class MercuryOrderUseCase {
    private static TissueSMIDDao tissueSMIDDao = new TissueSMIDDao();
    private static KitRequestDao kitRequestDao = new KitRequestDao();

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

    public static ArrayList<String> createBarcodes(MercurySampleDto[] mercurySampleDtos, DDPInstanceDto ddpInstance) {
        ArrayList<String> barcodes = new ArrayList<>();
        for (MercurySampleDto mercurySampleDto : mercurySampleDtos) {
            if (mercurySampleDto.sampleType.equals(MercurySampleDao.TISSUE_SAMPLE_TYPE)) {
                barcodes.addAll(tissueSMIDDao.getSequencingSmIdsForTissue(mercurySampleDto.tissueId));
            } else if (mercurySampleDto.sampleType.equals(MercurySampleDao.KIT_SAMPLE_TYPE)) {
                barcodes.addAll(kitRequestDao.getKitLabelFromDsmKitRequestId(mercurySampleDto.dsmKitRequestId));
            }
        }
        return barcodes;
    }
}
