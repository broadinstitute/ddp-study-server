package org.broadinstitute.dsm.route.tag.cohort;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.export.painless.RemoveFromNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.tags.cohort.CohortTagUseCase;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import spark.Request;
import spark.Response;

public class DeleteCohortTagRoute extends RequestHandler {
    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = Optional.ofNullable(request.queryMap().get(RoutePath.REALM).value()).orElseThrow().toLowerCase();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        Integer cohortTagId =
                Integer.valueOf(Optional.ofNullable(request.queryMap().get(ESObjectConstants.DSM_COHORT_TAG_ID).value()).orElseThrow());
        CohortTag cohortTagPayload = new CohortTag();
        cohortTagPayload.setCohortTagId(cohortTagId);
        CohortTagUseCase cohortTagUseCase = new CohortTagUseCase(cohortTagPayload, ddpInstanceDto, new CohortTagDaoImpl(),
                new ElasticSearch(), new NestedUpsertPainlessFacade(), new RemoveFromNestedScriptBuilder());
        cohortTagUseCase.delete();
        return ObjectMapperSingleton.writeValueAsString(cohortTagId);
    }
}
