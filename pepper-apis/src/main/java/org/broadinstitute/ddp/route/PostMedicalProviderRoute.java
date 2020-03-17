package org.broadinstitute.ddp.route;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.constants.SqlConstants.MedicalProviderTable;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.medicalprovider.PostMedicalProviderResponsePayload;
import org.broadinstitute.ddp.json.medicalprovider.PostPatchMedicalProviderRequestPayload;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.util.ResponseUtil;

import org.jdbi.v3.core.Handle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class PostMedicalProviderRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PostMedicalProviderRoute.class);

    @Override
    public PostMedicalProviderResponsePayload handle(
            Request request, Response response
    ) {
        String participantGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String institutionType = request.params(PathParam.INSTITUTION_TYPE);
        return TransactionWrapper.withTxn(
                handle -> {
                    UserDto userDto = Optional.ofNullable(handle.attach(JdbiUser.class).findByUserGuid(participantGuid)).orElseGet(
                            () -> {
                                LOG.info("A user with GUID {} was not found [{},{}]", participantGuid, request.pathInfo(), request.ip());
                                String errMsg = "The user was not found";
                                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                            }
                    );

                    long umbrellaStudyId = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid).orElseGet(
                            () -> {
                                LOG.info("A study with GUID {} was not found [{},{}]", studyGuid, request.pathInfo(), request.ip());
                                String errMsg = "The study was not found";
                                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                            }
                    );

                    PostPatchMedicalProviderRequestPayload newMedicalProviderData = StringUtils.isNotBlank(request.body())
                            ? tryDeserializeMedicalProvider(request.body(), response) : new PostPatchMedicalProviderRequestPayload();

                    String medicalProviderGuid = DBUtils.uniqueStandardGuid(
                            handle, MedicalProviderTable.TABLE_NAME, MedicalProviderTable.MEDICAL_PROVIDER_GUID
                    );

                    MedicalProviderDto newMedicalProviderDto = MedicalProviderDto.createUnspecifiedMedicalProvider(
                            medicalProviderGuid,
                            userDto.getUserId(),
                            umbrellaStudyId,
                            InstitutionType.fromUrlComponent(institutionType),
                            newMedicalProviderData
                    );

                    int numCreated = insertMedicalProvider(handle, newMedicalProviderDto);
                    boolean creationSucceeded = numCreated == 1;
                    if (creationSucceeded) {
                        logMedicalProviderCreationSuccess(participantGuid, medicalProviderGuid, studyGuid);
                        queueDataForExport(handle, userDto.getUserId(), umbrellaStudyId);
                    } else {
                        logMedicalProviderCreationFailure(participantGuid, studyGuid);
                    }

                    response.status(HttpStatus.SC_CREATED);
                    return new PostMedicalProviderResponsePayload(newMedicalProviderDto);
                }
        );
    }

    private int insertMedicalProvider(
            Handle handle,
            MedicalProviderDto medicalProviderDto
    ) {
        return handle.attach(MedicalProviderDao.class).insert(medicalProviderDto);
    }

    private void logMedicalProviderCreationFailure(String participantGuid, String studyGuid) {
        LOG.warn(
                "The number of created medical providers created by user {}"
                + " in the study {} is not equal to 1",
                participantGuid, studyGuid
        );
    }

    private void logMedicalProviderCreationSuccess(String participantGuid, String studyGuid, String medicalProviderGuid) {
        LOG.info(
                "The user {} successfully created a medical provider {} in the study {}",
                participantGuid, medicalProviderGuid, studyGuid
        );
    }

    private void queueDataForExport(Handle handle, long userId, long umbrellaStudyId) {
        handle.attach(DataExportDao.class).queueDataSync(userId, umbrellaStudyId);
    }

    private PostPatchMedicalProviderRequestPayload tryDeserializeMedicalProvider(String requestBody, Response response) {
        try {
            return new Gson().fromJson(
                    requestBody,
                    PostPatchMedicalProviderRequestPayload.class
            );
        } catch (JsonSyntaxException e) {
            String errMsg = "The payload does not represent a valid medical provider entity";
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.BAD_PAYLOAD, errMsg));
        }
    }

}
