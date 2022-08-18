package org.broadinstitute.dsm.db.dto.mercury;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.kitrequest.KitRequestDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dao.mercury.MercuryOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.ExportFacade;
import org.broadinstitute.dsm.model.elastic.export.ExportFacadePayload;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.mercury.BaseMercuryStatusMessage;
import org.broadinstitute.dsm.model.mercury.MercuryStatusMessage;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

@Slf4j
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

    public static void exportToES(List<MercuryOrderDto> newOrders) {
        ClinicalOrder clinicalOrder = new ClinicalOrder();
        newOrders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getDsmKitRequestId() != null).findFirst().ifPresent(order -> {
            clinicalOrder.setDsmKitRequestId(order.getDsmKitRequestId());
        });
        newOrders.stream().filter(mercuryOrderDto -> mercuryOrderDto.getTissueId() != null).findFirst().ifPresent(order -> {
            clinicalOrder.setTissueId(order.getTissueId());
        });
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
                    + "ElasticSearch for instance ", clinicalOrder.getOrderId(), ddpInstanceDto.getInstanceName()));
            e.printStackTrace();
        }
    }

    /**
     * This function is supposed to export the new status to ES,
     * but I can't get a hold of ddpInstance from the
     * BaseMercuryStatusMessage
     */
    public static void exportStatusToES(BaseMercuryStatusMessage baseMercuryStatusMessage, DDPInstanceDto ddpInstance) {
        String matchQueryName = "dsm.clinicalOrder.orderId";
        Optional<ElasticSearchParticipantDto> elasticSearchParticipantDto = null;
        String orderId = baseMercuryStatusMessage.getStatus().getOrderID();
        try {
            elasticSearchParticipantDto =
                    Optional.of(ElasticSearchUtil.getElasticSearchForGivenMatch(ddpInstance.getEsParticipantIndex(),
                            orderId, ElasticSearchUtil.getClientInstance(), matchQueryName));
        } catch (IOException e) {
            e.printStackTrace();
        }

        Patch patch = new Patch();
        patch.setId(orderId);
        MercuryStatusMessage mercuryStatusMessage = baseMercuryStatusMessage.getStatus();
        exportStatusFields("clinicalOrder.orderStatus", mercuryStatusMessage.getOrderStatus(), ddpInstance, elasticSearchParticipantDto,
                patch);
        exportStatusFields("clinicalOrder.orderDate", System.currentTimeMillis(), ddpInstance, elasticSearchParticipantDto, patch);
        exportStatusFields("clinicalOrder.mercuryPdoId", mercuryStatusMessage.getPdoKey(), ddpInstance, elasticSearchParticipantDto, patch);
        exportStatusFields("clinicalOrder.statusDetail", mercuryStatusMessage.getDetails(), ddpInstance, elasticSearchParticipantDto,
                patch);

    }

    private static void exportStatusFields(String fieldPath, Object fieldValue, DDPInstanceDto ddpInstance,
                                           Optional<ElasticSearchParticipantDto> elasticSearchParticipantDto, Patch patch) {
        GeneratorPayload
                generatorPayload = new GeneratorPayload(new NameValue(fieldPath, fieldValue), patch);
        ExportFacadePayload exportFacadePayload =
                new ExportFacadePayload(ddpInstance.getEsParticipantIndex(), elasticSearchParticipantDto.orElseThrow().getParticipantId(),
                        generatorPayload, ddpInstance.getInstanceName());
        ExportFacade exportFacade = new ExportFacade(exportFacadePayload);
        exportFacade.export();
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
