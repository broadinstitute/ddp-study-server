package org.broadinstitute.dsm.route.tag.cohort;

import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
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

public class CreateCohortTagRoute extends RequestHandler  {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = Optional.ofNullable(request.queryMap().get(RoutePath.REALM).value()).orElseThrow().toLowerCase();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        CohortTag cohortTagPayload = ObjectMapperSingleton.readValue(request.body(), new TypeReference<CohortTag>() {});
        cohortTagPayload.setDdpParticipantId(getGuidIfLegacyAltPid(ddpInstanceDto, cohortTagPayload));
        cohortTagPayload.setDdpInstanceId(ddpInstanceDto.getDdpInstanceId());
        CohortTagDao cohortTagDao = new CohortTagDaoImpl();
        int justCreatedCohortTagId = cohortTagDao.create(cohortTagPayload);
        cohortTagPayload.setCohortTagId(justCreatedCohortTagId);
        UpsertPainlessFacade.of(
                DBConstants.COHORT_ALIAS, cohortTagPayload, ddpInstanceDto,
                ESObjectConstants.DSM_COHORT_TAG_ID, ESObjectConstants.DOC_ID,
                getGuidIfLegacyAltPid(ddpInstanceDto, cohortTagPayload)).export();
        return ObjectMapperSingleton.writeValueAsString(justCreatedCohortTagId);
    }

    private String getGuidIfLegacyAltPid(DDPInstanceDto ddpInstanceDto, CohortTag cohortTagPayload) {
        String ddpParticipantId = cohortTagPayload.getDdpParticipantId();
        boolean isLegacyAltPid = ParticipantUtil.isLegacyAltPid(ddpParticipantId);
        if (isLegacyAltPid) {
            ElasticSearchable elasticSearchable = new ElasticSearch();
            ElasticSearchParticipantDto esDto =
                    elasticSearchable.getParticipantById(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
            ddpParticipantId = esDto.getParticipantId();
        }
        return ddpParticipantId;
    }
}
