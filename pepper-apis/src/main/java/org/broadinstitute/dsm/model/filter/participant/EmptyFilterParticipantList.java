package org.broadinstitute.dsm.model.filter.participant;

import java.util.Objects;

import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.QueryParamsMap;

public class EmptyFilterParticipantList extends BaseFilterParticipantList {


    @Override
    public ParticipantWrapperResult filter(QueryParamsMap queryParamsMap) {
        if(!Objects.requireNonNull(queryParamsMap).hasKey(RoutePath.REALM)) throw new RuntimeException("realm is necessary");
        prepareNeccesaryData(queryParamsMap);
        String realm = queryParamsMap.get(RoutePath.REALM).value();
        DDPInstanceDto ddpInstanceByGuid = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder()
                .withDdpInstanceDto(ddpInstanceByGuid)
                .withFrom(from)
                .withTo(to)
                .build();
        ElasticSearch elasticSearch = new ElasticSearch();
        return new ParticipantWrapper(participantWrapperPayload, elasticSearch).getFilteredList();
    }
}
