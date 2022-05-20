package org.broadinstitute.dsm.model.filter.prefilter.osteo;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.BaseStudyPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter;

import java.util.List;
import java.util.stream.Collectors;

public class OldOsteoPreFilter extends BaseStudyPreFilter {

    public static final String ACTIVITY_VERSION_1 = "v1";

    protected OldOsteoPreFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
    }

    public static StudyPreFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new OldOsteoPreFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }

    @Override
    public void filter() {

        List<ESActivities> filteredActivities = elasticSearchParticipantDto.getActivities().stream()
                .filter(this::isActivityNotUpdated)
                .collect(Collectors.toList());

        elasticSearchParticipantDto.setActivities(filteredActivities);

    }

    private boolean isActivityNotUpdated(ESActivities activity) {
        return ACTIVITY_VERSION_1.equals(activity.getActivityVersion());
    }
}
