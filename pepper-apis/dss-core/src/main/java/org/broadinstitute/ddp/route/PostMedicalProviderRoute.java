package org.broadinstitute.ddp.route;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class PostMedicalProviderRoute implements Route {
    @Override
    public PostMedicalProviderResponsePayload handle(Request request, Response response) {
        String participantGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String institutionType = request.params(PathParam.INSTITUTION_TYPE);
        return TransactionWrapper.withTxn(
                handle -> {
                    UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(participantGuid);
                    if (userDto == null) {
                        log.info("A user with GUID {} was not found [{},{}]", participantGuid, request.pathInfo(), request.ip());
                        String errMsg = "The user was not found";
                        throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }

                    Optional<Long> umbrellaStudyIdOpt = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);
                    if (umbrellaStudyIdOpt.isEmpty()) {
                        log.info("A study with GUID {} was not found [{},{}]", studyGuid, request.pathInfo(), request.ip());
                        String errMsg = "The study was not found";
                        throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }

                    long umbrellaStudyId = umbrellaStudyIdOpt.get();
                    PostPatchMedicalProviderRequestPayload newMedicalProviderJson;

                    String requestBody = request.body();
                    if (StringUtils.isNotBlank(requestBody)) {
                        try {
                            newMedicalProviderJson = new Gson().fromJson(
                                    request.body(),
                                    PostPatchMedicalProviderRequestPayload.class
                            );
                        } catch (JsonSyntaxException e) {
                            String errMsg = "The payload does not represent a valid medical provider entity";
                            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.BAD_PAYLOAD, errMsg));
                        }
                    } else {
                        newMedicalProviderJson = new PostPatchMedicalProviderRequestPayload();
                    }
                    
                    long userId = userDto.getUserId();
                    String medicalProviderGuid = DBUtils.uniqueStandardGuid(
                            handle, MedicalProviderTable.TABLE_NAME, MedicalProviderTable.MEDICAL_PROVIDER_GUID
                    );

                    InstitutionType institutionTypeCode = InstitutionType.fromUrlComponent(institutionType);

                    MedicalProviderDto newMedicalProviderDto = new MedicalProviderDto(
                            null,
                            medicalProviderGuid,
                            userId,
                            umbrellaStudyId,
                            institutionTypeCode,
                            newMedicalProviderJson.getInstitutionName(),
                            newMedicalProviderJson.getPhysicianName(),
                            newMedicalProviderJson.getCity(),
                            newMedicalProviderJson.getState(),
                            newMedicalProviderJson.getCountry(),
                            null,
                            null,
                            null,
                            null
                    );

                    int numCreated = handle.attach(MedicalProviderDao.class).insert(newMedicalProviderDto);
                    if (numCreated == 1) {
                        handle.attach(DataExportDao.class).queueDataSync(userId, umbrellaStudyId);
                        log.info(
                                "The user {} successfully created a medical provider {} in the study {}",
                                participantGuid, medicalProviderGuid, studyGuid
                        );
                    } else {
                        log.warn(
                                "The number of created medical providers ({}) created by user {}"
                                + " in the study {} is not equal to 1",
                                numCreated, participantGuid, studyGuid
                        );
                    }

                    response.status(HttpStatus.SC_CREATED);
                    return new PostMedicalProviderResponsePayload(newMedicalProviderDto);
                }
        );
    }

}
