package org.broadinstitute.dsm.model.filter.participant;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.query.AbstractQueryBuilderFactory;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseAbstractQueryBuilder;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import spark.QueryParamsMap;

public class QuickFilterParticipantList extends BaseFilterParticipantList {

    @Override
    public ParticipantWrapperResult filter(QueryParamsMap queryParamsMap) {
        prepareNecessaryData(queryParamsMap);
        ParticipantWrapperResult participantWrapperResult = new ParticipantWrapperResult();
        if (StringUtils.isBlank(quickFilterName) || StringUtils.isBlank(filterQuery)) {
            return participantWrapperResult;
        }
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();

        BaseAbstractQueryBuilder abstractQueryBuilder = AbstractQueryBuilderFactory.create("dsm", filterQuery);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> mainQuery = abstractQueryBuilder.build();

        abstractQueryBuilder = AbstractQueryBuilderFactory.create("m", filterQuery);
        abstractQueryBuilder.setParser(new FilterParser());
        AbstractQueryBuilder<?> mainQueryTest = abstractQueryBuilder.build();

        ParticipantWrapperPayload.Builder participantWrapperPayload =
                new ParticipantWrapperPayload.Builder().withDdpInstanceDto(ddpInstanceDto).withFrom(from).withTo(to).withSortBy(sortBy);
        ElasticSearch elasticSearch = new ElasticSearch();
        return new ParticipantWrapper(participantWrapperPayload.build(), elasticSearch).getFilteredList(mainQuery);
    }
}
