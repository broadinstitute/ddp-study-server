
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.postfilter.BaseStudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;


/**
 * A class that filters Activity and nested Medical Record, OncHistory and KitShippingRequest documents so that
 * information commingled in an Elasticsearch index is cleaned and tailored to the OS1 use case.
 */
public class ActivityAndDdpIdPostFilter extends BaseStudyPostFilter {

    private final StudyPostFilterStrategy<Activities> oldOsteoFilter;

    private final DsmDdpInstanceIdPostFilter ddpInstanceIdFilter;

    protected ActivityAndDdpIdPostFilter(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        super(elasticSearchParticipantDto, ddpInstanceDto);
        this.oldOsteoFilter = new OldOsteoPostFilterStrategy();
        this.ddpInstanceIdFilter = new DsmDdpInstanceIdPostFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }

    public static StudyPostFilter of(ElasticSearchParticipantDto elasticSearchParticipantDto, DDPInstanceDto ddpInstanceDto) {
        return new ActivityAndDdpIdPostFilter(elasticSearchParticipantDto, ddpInstanceDto);
    }

    @Override
    public void filter() {
        List<Activities> filteredActivities = elasticSearchParticipantDto.getActivities().stream()
                .filter(oldOsteoFilter)
                .collect(Collectors.toList());
        elasticSearchParticipantDto.setActivities(filteredActivities);
        this.ddpInstanceIdFilter.filter();
    }

}
