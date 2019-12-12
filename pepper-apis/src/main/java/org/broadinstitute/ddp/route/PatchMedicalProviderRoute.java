package org.broadinstitute.ddp.route;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.medicalprovider.PostPatchMedicalProviderRequestPayload;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.util.ResponseUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;


public class PatchMedicalProviderRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PatchMedicalProviderRoute.class);

    /**
     * Implements "skip-if-not-set" semantics used for partial updates for String JSON fields
     * When applied to each entity field, allows to overwrite only ones specified in the request payload
     * @param json The JsonObject to check
     * @param fieldName The field to check
     * @param defaultFieldValue The default value to return if the field is not set
     * @return The field in JSON or a default field value
     */
    private static String getStringJsonFieldOrDefaultValue(JsonObject json, String fieldName, String defaultFieldValue) {
        JsonElement fieldElement = json.get(fieldName);
        if (json.has(fieldName)) {
            return fieldElement.isJsonNull() ? null : fieldElement.getAsString();
        } else {
            return defaultFieldValue;
        }
    }

    @Override
    public Object handle(
            Request request, Response response
    ) {
        String participantGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String institutionType = request.params(PathParam.INSTITUTION_TYPE);
        String medicalProviderGuid = request.params(PathParam.MEDICAL_PROVIDER_GUID);

        TransactionWrapper.useTxn(
                handle -> {
                    Optional<MedicalProviderDto> existingMedicalProviderDtoOpt = handle.attach(JdbiMedicalProvider.class)
                            .getByGuid(medicalProviderGuid);
                    if (!existingMedicalProviderDtoOpt.isPresent()) {
                        String errMsg = "A medical provider with GUID " + medicalProviderGuid + " you try to update is not found";
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }
                    MedicalProviderDto existingMedicalProviderDto = existingMedicalProviderDtoOpt.get();
                    PostPatchMedicalProviderRequestPayload medicalProviderToUpdateJson = null;
                    String institutionName = null;
                    String physicianName = null;
                    String city = null;
                    String state = null;
                    try {
                        JsonElement data = new JsonParser().parse(request.body());
                        if (!data.isJsonObject() || data.getAsJsonObject().entrySet().size() == 0) {
                            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_BODY);
                        }
                        JsonObject payload = data.getAsJsonObject();
                        institutionName = getStringJsonFieldOrDefaultValue(
                                payload,
                                PostPatchMedicalProviderRequestPayload.Fields.INSTITUTION_NAME,
                                existingMedicalProviderDto.getInstitutionName()
                        );
                        physicianName = getStringJsonFieldOrDefaultValue(
                                payload,
                                PostPatchMedicalProviderRequestPayload.Fields.PHYSICIAN_NAME,
                                existingMedicalProviderDto.getPhysicianName()
                        );
                        city = getStringJsonFieldOrDefaultValue(
                                payload,
                                PostPatchMedicalProviderRequestPayload.Fields.CITY,
                                existingMedicalProviderDto.getCity()
                        );
                        state = getStringJsonFieldOrDefaultValue(
                                payload,
                                PostPatchMedicalProviderRequestPayload.Fields.STATE,
                                existingMedicalProviderDto.getState()
                        );
                    } catch (JsonSyntaxException e) {
                        String errMsg = "The payload does not represent a valid medical provider entity";
                        throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, errMsg));
                    }
                    InstitutionType institutionTypeCode = InstitutionType.fromUrlComponent(institutionType);
                    MedicalProviderDto medicalProviderToUpdateDto = new MedicalProviderDto(
                            null,
                            medicalProviderGuid,
                            existingMedicalProviderDto.getUserId(),
                            existingMedicalProviderDto.getUmbrellaStudyId(),
                            institutionTypeCode,
                            institutionName,
                            physicianName,
                            city,
                            state,
                            null,
                            null,
                            null,
                            null
                    );
                    int numUpdated = handle.attach(MedicalProviderDao.class).updateByGuid(medicalProviderToUpdateDto);
                    if (numUpdated == 1) {
                        handle.attach(DataExportDao.class).queueDataSync(participantGuid, studyGuid);
                        LOG.info(
                                "The user {} successfully updated a medical provider {} in the study {}",
                                participantGuid, medicalProviderGuid, studyGuid
                        );
                    } else {
                        LOG.warn(
                                "The number of updated medical providers ({}) updated by user {} in the study {} is not equal to 1",
                                numUpdated, participantGuid, studyGuid
                        );
                    }
                }
        );

        response.status(HttpStatus.SC_NO_CONTENT);
        return "";
    }

}
