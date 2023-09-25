package org.broadinstitute.dsm.db.dto.mercury;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.mercury.BaseMercuryStatusMessage;
import org.broadinstitute.dsm.model.mercury.MercuryStatusMessage;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Slf4j
public class MercuryOrderUseCase {
    private static TissueSMIDDao tissueSMIDDao = new TissueSMIDDao();
    private static KitRequestDao kitRequestDao = new KitRequestDao();

    public static List<MercuryOrderDto> createAllOrders(String[] barcodes, String ddpParticipantId, String orderId, String userId)
            throws NoSuchElementException {
        List<MercuryOrderDto> orders = new ArrayList<>();
        MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();
        Map<String, MercuryOrderDto> participantBarcodes = mercuryOrderDao.getPossibleBarcodesForParticipant(ddpParticipantId);
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

    public static void exportToES(List<MercuryOrderDto> newOrders) {
        ClinicalOrder clinicalOrder = new ClinicalOrder();
        newOrders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getDsmKitRequestId() != null).findFirst().ifPresent(order ->
            clinicalOrder.setDsmKitRequestId(order.getDsmKitRequestId()));
        newOrders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getTissueId() != null).findFirst().ifPresent(order ->
            clinicalOrder.setTissueId(order.getTissueId()));
        MercuryOrderDto order = newOrders.get(0);
        clinicalOrder.setDdpParticipantId(order.getDdpParticipantId());
        clinicalOrder.setOrderId(order.getOrderId());
        clinicalOrder.setDdpInstanceId(order.getDdpInstanceId());
        clinicalOrder.setOrderDate(order.getOrderDate());
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.valueOf(order.getDdpInstanceId())).orElseThrow();
        try {
            UpsertPainlessFacade.of(DBConstants.DDP_MERCURY_SEQUENCING_ALIAS, clinicalOrder, ddpInstanceDto,
                    ESObjectConstants.MERCURY_SEQUENCING_ID, ESObjectConstants.DOC_ID,
                    Exportable.getParticipantGuid(order.getDdpParticipantId(), ddpInstanceDto.getEsParticipantIndex()),
                    new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            log.error(String.format("Error inserting newly created clinical order: %s in "
                    + "ElasticSearch for instance %s ", clinicalOrder.getOrderId(), ddpInstanceDto.getInstanceName()));
            e.printStackTrace();
        }
    }

    public static void exportStatusToES(BaseMercuryStatusMessage baseMercuryStatusMessage, ClinicalOrder clinicalOrder, long statusDate) {
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId((int) clinicalOrder.getDdpInstanceId()).orElseThrow();
        MercuryStatusMessage mercuryStatusMessage = baseMercuryStatusMessage.getStatus();
        clinicalOrder.setOrderStatus(mercuryStatusMessage.getOrderStatus());
        clinicalOrder.setStatusDate(statusDate);
        clinicalOrder.setMercuryPdoId(mercuryStatusMessage.getPdoKey());
        clinicalOrder.setStatusDetail(mercuryStatusMessage.getDetails());

        try {
            UpsertPainlessFacade.of(DBConstants.DDP_MERCURY_SEQUENCING_ALIAS, clinicalOrder, ddpInstanceDto,
                    ESObjectConstants.MERCURY_SEQUENCING_ID, ESObjectConstants.DOC_ID,
                    Exportable.getParticipantGuid(clinicalOrder.getDdpParticipantId(), ddpInstanceDto.getEsParticipantIndex()),
                    new PutToNestedScriptBuilder()).export();
        } catch (Exception e) {
            log.error(String.format("Error inserting updated status for clinical order: %s in "
                    + "ElasticSearch for instance %s", clinicalOrder.getOrderId(), ddpInstanceDto.getInstanceName()));
            e.printStackTrace();
        }

    }

    public List<String> collectBarcodes(MercurySampleDto[] mercurySampleDtos) {
        ArrayList<String> barcodes = new ArrayList<>();
        for (MercurySampleDto mercurySampleDto : mercurySampleDtos) {
            if (mercurySampleDto.sampleType.equals(MercurySampleDao.TISSUE_SAMPLE_TYPE)) {
                barcodes.addAll(tissueSMIDDao.getSequencingSmIdsForTissue(mercurySampleDto.tissueId));
            } else if (mercurySampleDto.sampleType.equals(MercurySampleDao.KIT_SAMPLE_TYPE)) {
                barcodes.add(kitRequestDao.getKitLabelFromDsmKitRequestId(mercurySampleDto.dsmKitRequestId));
            }
        }
        return barcodes;
    }


}
