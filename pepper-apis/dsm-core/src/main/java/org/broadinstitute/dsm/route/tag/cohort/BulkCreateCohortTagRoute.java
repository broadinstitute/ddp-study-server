package org.broadinstitute.dsm.route.tag.cohort;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
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
import spark.Request;
import spark.Response;

public class BulkCreateCohortTagRoute extends RequestHandler {
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
        } else if (isFilteredPatients(bulkCohortTagPayload)) {
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
            filter.setFrom(0);
            filter.setTo(1);
            ParticipantWrapperResult result = filter.filter(request.queryMap());
            result.getTotalCount();

        } else {
            //to all patients
        }

        return null;
    }

    private boolean isFilteredPatients(BulkCohortTagPayload bulkCohortTagPayload) {
        return StringUtils.isNotBlank(bulkCohortTagPayload.getManualFilter()) || Objects.nonNull(bulkCohortTagPayload.getSavedFilter());
    }

    private boolean isSelectedPatients(BulkCohortTagPayload bulkCohortTagPayload) {
        return Objects.nonNull(bulkCohortTagPayload.getSelectedPatients()) && bulkCohortTagPayload.getSelectedPatients().size() > 0;
    }
}

