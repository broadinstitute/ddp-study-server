package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiInstitutionType;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.medicalprovider.GetMedicalProviderResponse;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class GetMedicalProviderListRoute implements Route {
    @Override
    public List<GetMedicalProviderResponse> handle(
            Request request, Response response
    ) {
        String participantGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String institutionType = request.params(PathParam.INSTITUTION_TYPE);
        return TransactionWrapper.withTxn(
                handle -> {
                    if (handle.attach(JdbiUser.class).findByUserGuid(participantGuid) == null) {
                        String errMsg = "A user with GUID " + participantGuid + " you try to get data for is not found";
                        ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }
                    InstitutionType institutionTypeCode = InstitutionType.fromUrlComponent(institutionType);
                    long institutionTypeId = handle.attach(JdbiInstitutionType.class).getIdByType(institutionTypeCode).get();
                    List<GetMedicalProviderResponse> medicalProviders = handle.attach(JdbiMedicalProvider.class)
                            .getAllByUserGuidStudyGuidAndInstitutionTypeId(participantGuid, studyGuid, institutionTypeId)
                            .stream()
                            .map(GetMedicalProviderResponse::new)
                            .collect(Collectors.toList());
                    log.info(
                            "Found {} medical providers for the user {} in the study {}",
                            medicalProviders.size(), participantGuid, studyGuid
                    );
                    return medicalProviders;
                }
        );
    }
}
