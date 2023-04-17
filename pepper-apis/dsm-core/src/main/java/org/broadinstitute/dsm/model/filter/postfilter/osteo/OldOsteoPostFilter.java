
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.postfilter.BaseStudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;


public class OldOsteoPostFilter extends BaseStudyPostFilter {

    private final StudyPostFilterStrategy<Activities> oldOsteoFilter;

    private final NewOsteoPostFilterStrategy oldOsteoOncHistoryFilter;

    protected OldOsteoPostFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
        this.oldOsteoFilter = new OldOsteoPostFilterStrategy();
        this.oldOsteoOncHistoryFilter = new NewOsteoPostFilterStrategy(ddpInstanceDto);
    }

    public static StudyPostFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new OldOsteoPostFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }

    @Override
    public void filter() {
        List<Activities> filteredActivities = elasticSearchParticipantDto.getActivities().stream()
                .filter(oldOsteoFilter)
                .collect(Collectors.toList());
        elasticSearchParticipantDto.setActivities(filteredActivities);
        elasticSearchParticipantDto.getDsm().ifPresent(esDsm -> {
            List<OncHistoryDetail> filteredOncHistoryDetails = esDsm.getOncHistoryDetail().stream()
                    .filter(oldOsteoOncHistoryFilter)
                    .collect(Collectors.toList());
            esDsm.setOncHistoryDetail(filteredOncHistoryDetails);
        });
    }

}
