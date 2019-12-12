package org.broadinstitute.ddp.route;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.time.DateUtils.MILLIS_PER_SECOND;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.MAX_ID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.Institution;
import org.broadinstitute.ddp.model.dsm.InstitutionRequests;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetDsmInstitutionRequestsRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetDsmInstitutionRequestsRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        LOG.info("Starting GetDsmInstitutionRequestRoute.handle");
        LOG.info("Checking Study and MaxId");
        String studyGuid = request.params(STUDY_GUID);
        if (studyGuid == null) {
            LOG.error("Study GUID not found in request");
            ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                    new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Study GUID is missing"));
            return null;
        }

        String createdSinceSecondsEpochString = request.params(MAX_ID);
        if (createdSinceSecondsEpochString == null) {
            LOG.error("maxId not found in request");
            ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.MISSING_MAX_ID, "maxId is missing"));
            return null;
        }

        long createdSince;

        try {
            createdSince = Long.parseLong(createdSinceSecondsEpochString) * MILLIS_PER_SECOND;
        } catch (NumberFormatException | DateTimeException e) {
            String error = "Couldn't parse " + createdSinceSecondsEpochString + " as an epoch value";
            LOG.error(error, e);
            ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.BAD_PAYLOAD, error));
            return null;
        }

        List<InstitutionRequests> responses = TransactionWrapper.withTxn(handle -> {
            Optional<Long> studyIdOpt = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);
            if (!studyIdOpt.isPresent()) {
                String error = "Study GUID not found in database " + studyGuid;
                LOG.error(error);
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                        new ApiError(ErrorCodes.MISSING_STUDY_GUID, error));
                return null;
            }

            List<EnrollmentStatusDto> enrollmentStatuses = handle.attach(JdbiUserStudyEnrollment.class)
                    .findByStudyGuidAfterOrEqualToInstant(studyGuid, createdSince)
                    .stream()
                    .filter(enrollmentStatusDto ->
                            enrollmentStatusDto.getEnrollmentStatus() == EnrollmentStatusType.ENROLLED)
                    .collect(Collectors.toList());

            return buildJsonForUsers(handle, enrollmentStatuses, studyIdOpt.get());
        });

        return responses;
    }

    private List<InstitutionRequests> buildJsonForUsers(Handle handle, List<EnrollmentStatusDto> enrolledUsers, long studyId) {
        List<InstitutionRequests> response = new ArrayList<>();
        if (enrolledUsers.isEmpty()) {
            return response;
        }

        List<Long> userIds = enrolledUsers
                .stream()
                .map(dto -> dto.getUserId())
                .collect(Collectors.toList());

        Map<Long, List<MedicalProviderDto>> userIdToMedicalProviderDtoList = handle.attach(JdbiMedicalProvider.class)
                .getAllByUsersAndStudyIds(userIds, studyId)
                .stream()
                .filter(dto -> !dto.isBlank())
                .collect(Collectors.groupingBy(MedicalProviderDto::getUserId));

        Map<Long, EnrollmentStatusDto> userIdToEnrolledUser = enrolledUsers
                .stream()
                .collect(Collectors.toMap(EnrollmentStatusDto::getUserId, dto -> dto));

        Map<Long, UserDto> userIdToUserDto = handle.attach(JdbiUser.class).findByUserIds(userIds)
                .stream()
                .collect(Collectors.toMap(UserDto::getUserId, dto -> dto));

        userIdToMedicalProviderDtoList.forEach((key, value) -> {
            List<Institution> institutionRecords = value
                    .stream()
                    .map(Institution::new)
                    .collect(toList());

            EnrollmentStatusDto enrollmentStatusDto = userIdToEnrolledUser.get(key);
            UserDto userDto = userIdToUserDto.get(key);

            response.add(new InstitutionRequests(enrollmentStatusDto.getValidFromMillis() / MILLIS_PER_SECOND,
                    StringUtils.isBlank(userDto.getLegacyAltPid()) ? enrollmentStatusDto.getUserGuid() : userDto.getLegacyAltPid(),
                    Instant.ofEpochMilli(enrollmentStatusDto.getValidFromMillis()).atZone(ZoneOffset.UTC).toString(),
                    institutionRecords));
        });

        return response;
    }
}
