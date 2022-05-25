package org.broadinstitute.dsm.route.tag.cohort;

import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.tags.cohort.CohortTagUseCase;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import spark.Request;
import spark.Response;

public class CreateCohortTagRoute extends RequestHandler  {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = Optional.ofNullable(request.queryMap().get(RoutePath.REALM).value()).orElseThrow().toLowerCase();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        CohortTag cohortTagPayload = ObjectMapperSingleton.readValue(request.body(), new TypeReference<CohortTag>() {});
        CohortTagUseCase cohortTagUseCase = new CohortTagUseCase(cohortTagPayload, ddpInstanceDto, new CohortTagDaoImpl(),
                new ElasticSearch(), new NestedUpsertPainlessFacade(), new PutToNestedScriptBuilder());
        int justCreatedCohortTagId = cohortTagUseCase.insert();
        return ObjectMapperSingleton.writeValueAsString(justCreatedCohortTagId);
    }




}
