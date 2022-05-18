package org.broadinstitute.dsm.route.tag.cohort;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.PutToNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.RemoveFromNestedScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.ParticipantUtil;
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
        CohortTagDao cohortTagDao = new CohortTagDaoImpl();
        cohortTagDao.delete(cohortTagId);
        CohortTag cohortTagPayload = new CohortTag();
        cohortTagPayload.setCohortTagId(cohortTagId);
        UpsertPainlessFacade.of(
                DBConstants.COHORT_ALIAS, cohortTagPayload, ddpInstanceDto,
                ESObjectConstants.DSM_COHORT_TAG_ID, ESObjectConstants.DSM_COHORT_TAG_ID,
                cohortTagId, new RemoveFromNestedScriptBuilder()).export();
        return ObjectMapperSingleton.writeValueAsString(cohortTagId);
    }
}
