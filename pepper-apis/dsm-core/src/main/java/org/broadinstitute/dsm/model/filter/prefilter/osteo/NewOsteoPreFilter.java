package org.broadinstitute.dsm.model.filter.prefilter.osteo;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.BaseStudyPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.HasDdpInstanceId;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NewOsteoPreFilter extends BaseStudyPreFilter {

    private final Predicate<HasDdpInstanceId> matchByDdpInstanceId;

    protected NewOsteoPreFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
        this.matchByDdpInstanceId = hasDdpInstanceId -> hasDdpInstanceId.extractDdpInstanceId() == ddpInstanceDto.getDdpInstanceId();
    }

    public static StudyPreFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new NewOsteoPreFilter(elasticSearchParticipantDto, ddpInstanceDto);
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