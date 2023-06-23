
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.postfilter.BaseStudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;

/**
 *  A class that filters nested MedicalRecord, OncHistory and KitShippingRequest documents so that
 *  information commingled in an Elasticsearch index is filtered to the specific DDP Instance the request pertains to.
 */
public class DsmDdpInstanceIdPostFilter extends BaseStudyPostFilter {

    private final StudyPostFilterStrategy<HasDdpInstanceId> ddpInstanceIdFilter;

    protected DsmDdpInstanceIdPostFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
        ddpInstanceIdFilter = new DdpInstanceIdPostFilterStrategy(ddpInstanceDto);
    }

    public static StudyPostFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new DsmDdpInstanceIdPostFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }

    @Override
    public void filter() {
        elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {
            List<MedicalRecord> filteredMedicalRecords = esDsm.getMedicalRecord().stream()
                    .filter(ddpInstanceIdFilter)
                    .collect(Collectors.toList());
            List<OncHistoryDetail> filteredOncHistoryDetails = esDsm.getOncHistoryDetail().stream()
                    .filter(ddpInstanceIdFilter)
                    .collect(Collectors.toList());
            List<KitRequestShipping> filteredKitRequestShippings = esDsm.getKitRequestShipping().stream()
                    .filter(ddpInstanceIdFilter)
                    .collect(Collectors.toList());
            List<SomaticResultUpload> filteredSomaticDocuments = esDsm.getSomaticResultUpload().stream()
                        .filter(ddpInstanceIdFilter)
                        .collect(Collectors.toList());
            List<ClinicalOrder> filteredClinicalOrders = esDsm.getClinicalOrder().stream()
                    .filter(ddpInstanceIdFilter)
                    .collect(Collectors.toList());
            esDsm.setMedicalRecord(filteredMedicalRecords);
            esDsm.setOncHistoryDetail(filteredOncHistoryDetails);
            esDsm.setKitRequestShipping(filteredKitRequestShippings);
            esDsm.setSomaticResultUpload(filteredSomaticDocuments);
            esDsm.setClinicalOrder(filteredClinicalOrders);
        });

    }

}
