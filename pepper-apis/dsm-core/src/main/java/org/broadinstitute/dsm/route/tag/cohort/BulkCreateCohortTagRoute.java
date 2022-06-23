package org.broadinstitute.dsm.route.tag.cohort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.export.painless.AddListToNestedByGuidScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.filter.participant.BaseFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.ManualFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.QuickFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.SavedFilterParticipantList;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.model.tags.cohort.BulkCohortTag;
import org.broadinstitute.dsm.model.tags.cohort.CohortTagUseCase;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class BulkCreateCohortTagRoute extends RequestHandler {

    private static final int THRESHOLD = 500;

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = Optional.ofNullable(request.queryMap().get(RoutePath.REALM).value()).orElseThrow().toLowerCase();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        BulkCohortTagPayload bulkCohortTagPayload = ObjectMapperSingleton.readValue(
                request.body(), new TypeReference<BulkCohortTagPayload>() {}
        );

        if (isSelectedPatients(bulkCohortTagPayload)) {
            BulkCohortTag bulkCohortTag =
                    new BulkCohortTag(bulkCohortTagPayload.getCohortTags(), bulkCohortTagPayload.getSelectedPatients());
            CohortTagUseCase cohortTagUseCase =
                    new CohortTagUseCase(bulkCohortTag, ddpInstanceDto, new CohortTagDaoImpl(), new ElasticSearch(),
                            new NestedUpsertPainlessFacade(), new AddListToNestedByGuidScriptBuilder());
            return cohortTagUseCase.bulkInsert();
        } else  {
            BaseFilterParticipantList filter = new ManualFilterParticipantList(StringUtils.EMPTY);
            if (Objects.nonNull(bulkCohortTagPayload.getManualFilter())) {
                filter = new ManualFilterParticipantList(bulkCohortTagPayload.getManualFilter());
            } else if (Objects.nonNull(bulkCohortTagPayload.getSavedFilter())) {

                if (StringUtils.isNotBlank(bulkCohortTagPayload.getSavedFilter().getFilterName())) {
                    filter = new QuickFilterParticipantList();
                } else if (Objects.nonNull(bulkCohortTagPayload.getSavedFilter().getFilters())
                        && bulkCohortTagPayload.getSavedFilter().getFilters().length > 0) {
                    filter = new SavedFilterParticipantList();
                }
            }
            return insertCohortTags(request.queryMap(), ddpInstanceDto, bulkCohortTagPayload, filter);
        }
    }

    private List<CohortTag> insertCohortTags(QueryParamsMap queryMap, DDPInstanceDto ddpInstanceDto,
                                             BulkCohortTagPayload bulkCohortTagPayload, BaseFilterParticipantList filter) {
        List<CohortTag> resultToReturn = new ArrayList<>();

        setInitialRange(filter);

        ParticipantWrapperResult filteredResult = filter.filter(queryMap);

        long totalPages = calculateTotalPages(filteredResult);

        for (int i = 0; i < totalPages; i++) {
            if (isNotFirstPage(i)) {
                filter.setFrom(i * THRESHOLD);
                filter.setTo((i + 1) * THRESHOLD);
                filteredResult = filter.filter(queryMap);
            }
            List<String> selectedPatients = extractSelectedPatientsGuids(filteredResult);
            BulkCohortTag bulkCohortTag = new BulkCohortTag(bulkCohortTagPayload.getCohortTags(), selectedPatients);
            CohortTagUseCase cohortTagUseCase =
                    new CohortTagUseCase(bulkCohortTag, ddpInstanceDto, new CohortTagDaoImpl(), new ElasticSearch(),
                            new NestedUpsertPainlessFacade(), new AddListToNestedByGuidScriptBuilder());
            List<CohortTag> createdCohortTags = cohortTagUseCase.bulkInsert();
            resultToReturn.addAll(createdCohortTags);
        }
        return resultToReturn;
    }

    private List<String> extractSelectedPatientsGuids(ParticipantWrapperResult filteredResult) {
        return filteredResult.getParticipants().stream()
                .map(participantWrapperDto -> participantWrapperDto.getEsData().getProfile().map(ESProfile::getGuid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private long calculateTotalPages(ParticipantWrapperResult firstResult) {
        long totalCount = firstResult.getTotalCount();
        long totalPages = totalCount / THRESHOLD;
        if (totalCount % THRESHOLD > 0) {
            totalPages++;
        }
        return totalPages;
    }

    private void setInitialRange(BaseFilterParticipantList filter) {
        int from = 0;
        int to = THRESHOLD;
        filter.setFrom(from);
        filter.setTo(to);
    }

    private boolean isNotFirstPage(int i) {
        return i != 0;
    }

    private boolean isSelectedPatients(BulkCohortTagPayload bulkCohortTagPayload) {
        return Objects.nonNull(bulkCohortTagPayload.getSelectedPatients()) && bulkCohortTagPayload.getSelectedPatients().size() > 0;
    }
}

