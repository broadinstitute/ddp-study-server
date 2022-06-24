package org.broadinstitute.dsm.model.tags.cohort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.export.painless.AddListToNestedByGuidScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;

public class FilteredOrAllPatientsCohortStrategy extends BaseCohortStrategy {
    private static final int THRESHOLD = 500;

    public FilteredOrAllPatientsCohortStrategy() {
    }

    @Override
    public List<CohortTag> create() {
        PatientsFilterFactory patientsFilterFactory = new PatientsFilterFactory(bulkCohortTagPayload);
        return insertCohortTags(patientsFilterFactory.instance());
    }

    private List<CohortTag> insertCohortTags(Filterable<ParticipantWrapperResult> filter) {
        List<CohortTag> resultToReturn = new ArrayList<>();

        setInitialRange(filter);

        ParticipantWrapperResult filteredResult = filter.filter(getQueryMap());

        long totalPages = calculateTotalPages(filteredResult);

        for (int i = 0; i < totalPages; i++) {
            if (isNotFirstPage(i)) {
                filter.setFrom(i * THRESHOLD);
                filter.setTo((i + 1) * THRESHOLD);
                filteredResult = filter.filter(getQueryMap());
            }
            List<String> selectedPatients = extractSelectedPatientsGuids(filteredResult);
            BulkCohortTag bulkCohortTag = new BulkCohortTag(bulkCohortTagPayload.getCohortTags(), selectedPatients);
            CohortTagUseCase cohortTagUseCase =
                    new CohortTagUseCase(bulkCohortTag, getDDPInstanceDto(), new CohortTagDaoImpl(), new ElasticSearch(),
                            new NestedUpsertPainlessFacade(), new AddListToNestedByGuidScriptBuilder());
            List<CohortTag> createdCohortTags = cohortTagUseCase.bulkInsert();
            resultToReturn.addAll(createdCohortTags);
        }
        return resultToReturn;
    }

    private void setInitialRange(Filterable<ParticipantWrapperResult> filter) {
        int from = 0;
        int to = THRESHOLD;
        filter.setFrom(from);
        filter.setTo(to);
    }

    private long calculateTotalPages(ParticipantWrapperResult firstResult) {
        long totalCount = firstResult.getTotalCount();
        long totalPages = totalCount / THRESHOLD;
        if (totalCount % THRESHOLD > 0) {
            totalPages++;
        }
        return totalPages;
    }

    private boolean isNotFirstPage(int i) {
        return i != 0;
    }

    private List<String> extractSelectedPatientsGuids(ParticipantWrapperResult filteredResult) {
        return filteredResult.getParticipants().stream()
                .map(participantWrapperDto -> participantWrapperDto.getEsData().getProfile().map(ESProfile::getGuid))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}
