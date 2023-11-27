package org.broadinstitute.ddp.route;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.time.DateUtils.MILLIS_PER_SECOND;
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

import lombok.extern.slf4j.Slf4j;
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
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class GetDsmInstitutionRequestsRoute implements Route {
    @Override
    public Object handle(Request request, Response response) {
        log.info("Starting GetDsmInstitutionRequestRoute.handle");
        log.info("Checking Study and MaxId");
        String studyGuid = request.params(STUDY_GUID);
        if (studyGuid == null) {
            log.error("Study GUID not found in request");
            ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                    new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Study GUID is missing"));
            return null;
        }

        String createdSinceSecondsEpochString = request.params(MAX_ID);
        if (createdSinceSecondsEpochString == null) {
            log.error("maxId not found in request");
            ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.MISSING_MAX_ID, "maxId is missing"));
            return null;
        }

        long createdSince;

        try {
            createdSince = Long.parseLong(createdSinceSecondsEpochString) * MILLIS_PER_SECOND;
        } catch (NumberFormatException | DateTimeException e) {
            String error = "Couldn't parse " + createdSinceSecondsEpochString + " as an epoch value";
            log.error(error, e);
            ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.BAD_PAYLOAD, error));
            return null;
        }

        return TransactionWrapper.withTxn(handle -> {
            Optional<Long> studyIdOpt = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);
            if (studyIdOpt.isEmpty()) {
                String error = "Study GUID not found in database " + studyGuid;
                log.error(error);
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                        new ApiError(ErrorCodes.MISSING_STUDY_GUID, error));
                return null;
            }

            List<EnrollmentStatusDto> enrollmentStatuses = handle.attach(JdbiUserStudyEnrollment.class)
                    .findByStudyGuidAfterOrEqualToInstant(studyGuid, createdSince)
                    .stream()
                    .filter(enrollmentStatusDto ->
                            enrollmentStatusDto.getEnrollmentStatus().isEnrolled())
                    .collect(Collectors.toList());

            return buildJsonForUsers(handle, enrollmentStatuses, studyIdOpt.get());
        });
    }

    private List<InstitutionRequests> buildJsonForUsers(Handle handle, List<EnrollmentStatusDto> enrolledUsers, long studyId) {
        List<InstitutionRequests> response = new ArrayList<>();
        if (enrolledUsers.isEmpty()) {
            return response;
        }

        List<Long> userIds = enrolledUsers
                .stream()
                .map(EnrollmentStatusDto::getUserId)
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
