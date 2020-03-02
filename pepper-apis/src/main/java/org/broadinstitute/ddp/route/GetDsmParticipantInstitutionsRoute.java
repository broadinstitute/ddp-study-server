package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityMapping;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.types.ActivityMappingType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.Institution;
import org.broadinstitute.ddp.model.dsm.ParticipantInstitution;
import org.broadinstitute.ddp.model.dsm.StudyActivityMapping;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetDsmParticipantInstitutionsRoute implements Route {
    private static final Logger logger = LoggerFactory.getLogger(GetDsmParticipantInstitutionsRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        return TransactionWrapper.withTxn(handle -> {
            logger.info("Starting GetDsmParticipantInstitutionsRoute.handle");
            logger.info("Checking Study GUID");
            String studyGuid = request.params(STUDY_GUID);
            if (studyGuid == null) {
                logger.error("Study GUID not found in request");
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Study GUID is missing"));
            }

            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String errorMessage = "Study not found for: " + studyGuid;
                logger.error(errorMessage);
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                        new ApiError(ErrorCodes.STUDY_NOT_FOUND, errorMessage));
            }

            List<EnrollmentStatusDto> allUsers = handle.attach(JdbiUserStudyEnrollment.class).findByStudyGuid(studyGuid);

            List<EnrollmentStatusDto> includedUsers = allUsers.stream()
                    .filter(enrollmentStatusDto ->
                            enrollmentStatusDto.getEnrollmentStatus() == EnrollmentStatusType.ENROLLED
                                    || enrollmentStatusDto.getEnrollmentStatus() == EnrollmentStatusType.EXITED_AFTER_ENROLLMENT)
                    .collect(Collectors.toList());

            if (includedUsers.isEmpty()) {
                return new ArrayList<>();
            } else {
                try {
                    return buildJsonForParticipants(handle, includedUsers, studyDto.getId(), response, studyGuid);
                } catch (Exception e) {
                    logger.error("Could not build JSON response", e);
                    throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            new ApiError(ErrorCodes.SERVER_ERROR, "Could not build JSON response"));
                }
            }
        });
    }

    private List<ParticipantInstitution> buildJsonForParticipants(Handle handle,
                                                                  List<EnrollmentStatusDto> enrolledUsers,
                                                                  long studyId,
                                                                  Response response,
                                                                  String studyGuid) throws Exception {
        List<ParticipantInstitution> participantInstitutionList = new ArrayList<>();

        List<Long> userIds = enrolledUsers.stream()
                .map(EnrollmentStatusDto::getUserId)
                .collect(Collectors.toList());

        Map<Long, List<MedicalProviderDto>> medicalRecordsByUserId = handle.attach(JdbiMedicalProvider.class)
                .getAllByUsersAndStudyIds(userIds, studyId)
                .stream()
                .filter(dto -> !dto.isBlank())
                .collect(Collectors.groupingBy(MedicalProviderDto::getUserId));

        List<StudyActivityMapping> studyActivityMappings = handle.attach(JdbiActivityMapping.class)
                .getActivityMappingForStudyAndActivityType(studyId, ActivityMappingType.MEDICAL_RELEASE)
                .collect(Collectors.toList());
        if (studyActivityMappings.isEmpty()) {
            String errorMessage = "Activity mapping: " + ActivityMappingType.MEDICAL_RELEASE + " not found for: " + studyGuid;
            logger.error(errorMessage);
            throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                    new ApiError(ErrorCodes.STUDY_NOT_FOUND, errorMessage));
        }

        Set<Long> releaseActivityIds = studyActivityMappings.stream()
                .map(StudyActivityMapping::getStudyActivityId)
                .collect(Collectors.toSet());
        Map<Long, List<ActivityResponse>> userIdToReleaseInstances = handle.attach(ActivityInstanceDao.class)
                .findBaseResponsesByStudyAndUserIds(studyId, Set.copyOf(userIds), true, releaseActivityIds)
                .collect(Collectors.groupingBy(ActivityResponse::getParticipantId));

        for (Map.Entry<Long, List<MedicalProviderDto>> user : medicalRecordsByUserId.entrySet()) {
            long userId = user.getKey();
            List<MedicalProviderDto> medicalProviderDtoList = user.getValue();

            if (medicalProviderDtoList.isEmpty()) {
                logger.debug("Encountered user who has completed survey but has no institutions");
                continue;
            }

            List<Institution> institutionList = medicalProviderDtoList
                    .stream()
                    .map(Institution::new)
                    .collect(Collectors.toList());

            UserDto userDto = handle.attach(JdbiUser.class).findByUserId(userId);
            String userGuid = userDto.getUserGuid();

            Optional<MailAddress> mailAddressResult = handle.attach(JdbiMailAddress.class)
                    .findDefaultAddressForParticipant(userGuid);

            ParticipantInstitution.Address address = new ParticipantInstitution.Address(mailAddressResult.orElse(null));

            UserProfileDto userProfileDto = handle.attach(JdbiProfile.class)
                    .getUserProfileByUserId(userId);

            List<EnrollmentStatusDto> enrollmentStatusDtos = handle.attach(JdbiUserStudyEnrollment.class)
                    .getAllEnrollmentStatusesByUserAndStudyIdsSortedDesc(userId, studyId);
            Optional<EnrollmentStatusDto> userExitedBeforeEnrollment = enrollmentStatusDtos.stream()
                    .filter(enrollmentStatusDto ->
                            enrollmentStatusDto.getEnrollmentStatus() == EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT)
                    .sorted(Comparator.comparing(EnrollmentStatusDto::getValidFromMillis).reversed())
                    .findFirst();
            if (userExitedBeforeEnrollment.isPresent()) {
                logger.error("Found user " + userGuid + " exited before enrollment");
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Could not build JSON response"));
            }

            ActivityResponse latestInstance = userIdToReleaseInstances.getOrDefault(userId, Collections.emptyList())
                    .stream()
                    .filter(instance -> instance.getFirstCompletedAt() != null)
                    .max(Comparator.comparing(ActivityResponse::getFirstCompletedAt))
                    .orElse(null);

            if (latestInstance == null) {
                logger.error("No completed release survey found for user " + userGuid);
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Could not build JSON response"));
            }

            ParticipantInstitution participantInstitution = new ParticipantInstitution(
                    userProfileDto.getFirstName(),
                    userProfileDto.getLastName(),
                    userDto.getUserHruid(),
                    userDto.getLegacyShortId(),
                    mailAddressResult.map(MailAddress::getCountry).orElse(null),
                    latestInstance.getCreatedAt(),
                    latestInstance.getLatestStatus().getUpdatedAt(),
                    latestInstance.getFirstCompletedAt(),
                    mailAddressResult.map(mailAddress -> {
                        try {
                            return DsmAddressValidationStatus.addressValidStatuses()
                                    .contains(DsmAddressValidationStatus.getByCode(mailAddress.getValidationStatus())) ? 1 : 0;
                        } catch (Exception e) {
                            return 0;
                        }
                    }).orElse(0),
                    StringUtils.isEmpty(userDto.getLegacyAltPid()) ? userDto.getUserGuid() : userDto.getLegacyAltPid(),
                    address,
                    institutionList
            );

            participantInstitutionList.add(participantInstitution);
        }

        return participantInstitutionList;
    }
}
