package org.broadinstitute.dsm.model.tags.cohort;

import java.util.Map;

import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;

public class BulkCohortTagCreationStrategyFactory {
    private BulkCohortTagPayload bulkCohortTagPayload;
    private static final Map<CreateOption, CohortStrategy> strategyMap = Map.of(
            CreateOption.ALL_PATIENTS, new FilteredOrAllPatientsCohortStrategy(),
            CreateOption.SELECTED_PATIENTS, new BaseCohortStrategy()
    );

    public BulkCohortTagCreationStrategyFactory(BulkCohortTagPayload bulkCohortTagPayload) {
        this.bulkCohortTagPayload = bulkCohortTagPayload;
    }

    public CohortStrategy instance() {
        CohortStrategy strategy = strategyMap.get(bulkCohortTagPayload.getSelectedOption());
        strategy.setBulkCohortTagPayload(bulkCohortTagPayload);
        return strategy;
    }
}
