package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.postfilter.BaseStudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;

public class OldOsteoPostFilter extends BaseStudyPostFilter {

    public static final String ACTIVITY_VERSION_1 = "v1";
    public static final String FAMILY_HISTORY_ACTIVITY = "FAMILY_HISTORY";
    public static final String ADD_PARTICIPANT_ACTIVITY = "ADD_PARTICIPANT";

    protected OldOsteoPostFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
    }

    public static StudyPostFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new OldOsteoPostFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }

    @Override
    public void filter() {
        List<Activities> filteredActivities = elasticSearchParticipantDto.getActivities().stream()
                .filter(this::isActivityNotOsteo2)
                .collect(Collectors.toList());
        elasticSearchParticipantDto.setActivities(filteredActivities);
    }

    private boolean isActivityNotOsteo2(Activities activity) {
        return ACTIVITY_VERSION_1.equals(activity.getActivityVersion()) && !activity.getActivityCode().startsWith(FAMILY_HISTORY_ACTIVITY)
                && !activity.getActivityCode().equals(ADD_PARTICIPANT_ACTIVITY);
    }
}
