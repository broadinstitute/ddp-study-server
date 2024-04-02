package org.broadinstitute.dsm.service.download;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.DSM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.elastic.search.UnparsedDeserializer;
import org.broadinstitute.dsm.model.elastic.search.UnparsedESParticipantDto;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import spark.QueryParamsMap;

public class DownloadParticipantListService {
    /**
     * Fetches participant information from ElasticSearch in batches of MAX_RESULT_SIZE
     */
    public List<ParticipantWrapperDto> fetchParticipantEsData(Filterable filter, QueryParamsMap queryParamsMap) {
        List<ParticipantWrapperDto> allResults = new ArrayList<>();
        int currentFrom = DEFAULT_FROM;
        int currentTo = MAX_RESULT_SIZE;
        while (true) {
            // For each batch of results, add the DTOs to the allResults list
            filter.setFrom(currentFrom);
            filter.setTo(currentTo);
            ParticipantWrapperResult filteredSubset = (ParticipantWrapperResult) filter.filter(queryParamsMap, new UnparsedDeserializer());
            filteredSubset.getParticipants().forEach(participantWrapperDto -> {
                        UnparsedESParticipantDto participantDto = (UnparsedESParticipantDto) participantWrapperDto.getEsData();
                        HashMap<String, List> dsmData =  (HashMap<String, List>) participantDto.getDataAsMap().get(DSM);
                        if (dsmData != null && dsmData.get("kitRequestShipping") != null) {
                            ((List<Map<String, Object>>) dsmData.get("kitRequestShipping"))
                                    .forEach(kit -> kit.put("sampleQueue", KitRequestShipping.getSampleQueueValue(kit)));
                        }
                    }
            );
            allResults.addAll(filteredSubset.getParticipants());
            // if the total count is less than the range we are currently on, stop fetching
            if (filteredSubset.getTotalCount() < currentTo) {
                break;
            }
            currentFrom = currentTo;
            currentTo += MAX_RESULT_SIZE;
        }

        return allResults;
    }
}
