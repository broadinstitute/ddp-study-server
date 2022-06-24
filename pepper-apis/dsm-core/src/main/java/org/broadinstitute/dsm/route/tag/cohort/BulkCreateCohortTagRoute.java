package org.broadinstitute.dsm.route.tag.cohort;

import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.model.tags.cohort.BulkCohortTagCreationStrategyFactory;
import org.broadinstitute.dsm.model.tags.cohort.CohortStrategy;
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
        BulkCohortTagPayload bulkCohortTagPayload = ObjectMapperSingleton.instance().readValue(
                request.body(), BulkCohortTagPayload.class
        );

        BulkCohortTagCreationStrategyFactory bulkCohortTagCreationStrategyFactory =
                new BulkCohortTagCreationStrategyFactory(request.queryMap(), ddpInstanceDto, bulkCohortTagPayload);
        CohortStrategy bulkCohortStrategy = bulkCohortTagCreationStrategyFactory.instance();
        return bulkCohortStrategy.create();
    }
}

