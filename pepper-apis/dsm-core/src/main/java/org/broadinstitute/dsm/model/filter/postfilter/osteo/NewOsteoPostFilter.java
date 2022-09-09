
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.postfilter.BaseStudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;

public class NewOsteoPostFilter extends BaseStudyPostFilter {

    private final Predicate<HasDdpInstanceId> matchByDdpInstanceId;

    protected NewOsteoPostFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
        this.matchByDdpInstanceId = hasDdpInstanceId -> hasDdpInstanceId.extractDdpInstanceId()
                .filter(instanceId -> instanceId.longValue() == ddpInstanceDto.getDdpInstanceId())
                .isPresent();
    }

    public static StudyPostFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new NewOsteoPostFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }

    @Override
    public void filter() {
        elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {
            List<MedicalRecord> filteredMedicalRecords = esDsm.getMedicalRecord().stream()
                    .filter(matchByDdpInstanceId)
                    .collect(Collectors.toList());
            List<OncHistoryDetail> filteredOncHistoryDetails = esDsm.getOncHistoryDetail().stream()
                    .filter(matchByDdpInstanceId)
                    .collect(Collectors.toList());
            List<KitRequestShipping> filteredKitRequestShippings = esDsm.getKitRequestShipping().stream()
                    .filter(matchByDdpInstanceId)
                    .collect(Collectors.toList());
            esDsm.setMedicalRecord(filteredMedicalRecords);
            esDsm.setOncHistoryDetail(filteredOncHistoryDetails);
            esDsm.setKitRequestShipping(filteredKitRequestShippings);
        });

    }

}
