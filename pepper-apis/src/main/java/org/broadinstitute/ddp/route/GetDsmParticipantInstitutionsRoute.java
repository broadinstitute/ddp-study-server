package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivityMapping;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.json.errors.ApiError;
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
                ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Study GUID is missing"));
                return null;
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
                    Optional<Long> studyIdResponse = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);

                    if (!studyIdResponse.isPresent()) {
                        String errorMessage = "Study not found for: " + studyGuid;
                        logger.error(errorMessage);
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                                new ApiError(ErrorCodes.STUDY_NOT_FOUND, errorMessage));
                        return null;
                    }

                    return buildJsonForParticipants(handle, includedUsers, studyIdResponse.get(), response, studyGuid);
                } catch (Exception e) {
                    logger.error("Could not build JSON response", e);
                    ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            new ApiError(ErrorCodes.SERVER_ERROR, "Could not build JSON response"));
                    return null;
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

        Map<Long, EnrollmentStatusDto> userIdToEnrolledUser = enrolledUsers
                .stream()
                .collect(Collectors.toMap(EnrollmentStatusDto::getUserId, dto -> dto));

        Map<Long, List<MedicalProviderDto>> medicalRecordsByUserId = handle.attach(JdbiMedicalProvider.class)
                .getAllByUsersAndStudyIds(userIds, studyId)
                .stream()
                .filter(dto -> !dto.isBlank())
                .collect(Collectors.groupingBy(MedicalProviderDto::getUserId));

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

            String userGuid = userIdToEnrolledUser.get(userId).getUserGuid();

            Optional<MailAddress> mailAddressResult = handle.attach(JdbiMailAddress.class)
                    .findDefaultAddressForParticipant(userGuid);

            ParticipantInstitution.Address address = new ParticipantInstitution.Address(mailAddressResult.orElse(null));

            UserProfileDto userProfileDto = handle.attach(JdbiProfile.class)
                    .getUserProfileByUserId(userId);

            Optional<StudyActivityMapping> studyActivityMapping = handle.attach(JdbiActivityMapping.class)
                    .getActivityMappingForStudyAndActivityType(studyGuid, ActivityMappingType.MEDICAL_RELEASE.toString());

            if (!studyActivityMapping.isPresent()) {
                String errorMessage = "Activity mapping: " + ActivityMappingType.MEDICAL_RELEASE.toString()
                        + " not found for: " + studyGuid;
                logger.error(errorMessage);
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                        new ApiError(ErrorCodes.STUDY_NOT_FOUND, errorMessage));
            }

            List<EnrollmentStatusDto> enrollmentStatusDtos = handle.attach(JdbiUserStudyEnrollment.class)
                    .getAllEnrollmentStatusesByUserAndStudyIds(userId, studyId);

            Optional<EnrollmentStatusDto> surveyCreated = enrollmentStatusDtos.stream()
                    .filter(enrollmentStatusDto -> enrollmentStatusDto.getEnrollmentStatus() == EnrollmentStatusType.REGISTERED)
                    .sorted(Comparator.comparing(EnrollmentStatusDto::getValidFromMillis).reversed())
                    .findFirst();

            // If we got this far, it means a survey was completed. This has to be 1 entry
            if (!surveyCreated.isPresent()) {
                logger.error("No registered entry found for activity "
                        + studyActivityMapping.get().getStudyActivityId()
                        + " and user " + userId);
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Could not build JSON response"));
            }

            long timeFirstCreated = surveyCreated.get().getValidFromMillis();


            Optional<EnrollmentStatusDto> userExitedBeforeEnrollment = enrollmentStatusDtos.stream()
                    .filter(enrollmentStatusDto ->
                            enrollmentStatusDto.getEnrollmentStatus() == EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT)
                    .sorted(Comparator.comparing(EnrollmentStatusDto::getValidFromMillis).reversed())
                    .findFirst();

            if (userExitedBeforeEnrollment.isPresent()) {
                logger.error("Found for study activity "
                        + studyActivityMapping.get().getStudyActivityId()
                        + " and user " + userId
                        + " user exited before enrollment");
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Could not build JSON response"));
            }


            Optional<EnrollmentStatusDto> surveyCompleted = enrollmentStatusDtos.stream()
                    .filter(enrollmentStatusDto -> enrollmentStatusDto.getEnrollmentStatus() == EnrollmentStatusType.ENROLLED)
                    .sorted(Comparator.comparing(EnrollmentStatusDto::getValidFromMillis).reversed())
                    .findFirst();


            // If we got this far, it means a survey was completed. This has to be 1 entry
            if (!surveyCompleted.isPresent()) {
                logger.error("No registered entry found for activity "
                        + studyActivityMapping.get().getStudyActivityId()
                        + " and user " + userId);
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Could not build JSON response"));
            }

            long timeFirstCompleted = surveyCompleted.get().getValidFromMillis();

            UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);

            ParticipantInstitution participantInstitution = new ParticipantInstitution(
                    userProfileDto.getFirstName(),
                    userProfileDto.getLastName(),
                    userDto.getUserHruid(),
                    userDto.getLegacyShortId(),
                    mailAddressResult.map(MailAddress::getCountry).orElse(null),
                    timeFirstCreated,
                    userIdToEnrolledUser.get(userId).getValidFromMillis(),
                    timeFirstCompleted,
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
