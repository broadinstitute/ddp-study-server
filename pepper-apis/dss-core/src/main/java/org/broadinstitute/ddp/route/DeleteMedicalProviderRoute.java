package org.broadinstitute.ddp.route;

import java.util.Optional;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;


public class DeleteMedicalProviderRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMedicalProviderRoute.class);

    @Override
    public Object handle(
            Request request, Response response
    ) {
        String medicalProviderGuid = request.params(PathParam.MEDICAL_PROVIDER_GUID);
        String participantGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        TransactionWrapper.useTxn(
                handle -> {
                    Optional<MedicalProviderDto> medicalProviderDtoOpt = handle.attach(JdbiMedicalProvider.class)
                            .getByGuid(medicalProviderGuid);
                    if (!medicalProviderDtoOpt.isPresent()) {
                        String errMsg = "A medical provider with GUID " + medicalProviderGuid + " you try to delete is not found";
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }
                    MedicalProviderDto medicalProviderDto = medicalProviderDtoOpt.get();
                    int numDeleted = handle.attach(JdbiMedicalProvider.class).deleteByGuid(medicalProviderGuid);
                    if (numDeleted == 1) {
                        handle.attach(DataExportDao.class).queueDataSync(participantGuid, studyGuid);
                        LOG.info(
                                "The user {} successfully deleted a medical provider {} in the study {},"
                                        + " institutionName = {}, physicianName = {}, city = {}, state = {}",
                                participantGuid, medicalProviderGuid, studyGuid,
                                medicalProviderDto.getInstitutionName(), medicalProviderDto.getPhysicianName(),
                                medicalProviderDto.getCity(), medicalProviderDto.getState()
                        );
                    } else {
                        LOG.warn(
                                "The number of deleted medical providers ({}) deleted by user {}"
                                        + " in the study {} is not equal to 1",
                                numDeleted, participantGuid, studyGuid
                        );
                    }
                }
        );

        response.status(HttpStatus.SC_NO_CONTENT);
        return "";
    }

}
