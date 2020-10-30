package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmStudyParticipantDao;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.dsm.DsmStudyParticipant;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.Institution;
import org.broadinstitute.ddp.model.dsm.Participant;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.MedicalRecordService;
import org.broadinstitute.ddp.transformers.DateTimeFormatUtils;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Given the study and participant GUID, returns the medical record for the user
 * containing information such as date of birth, list of institutions and samples he/she
 * consented to etc.
 */
public class GetDsmMedicalRecordRoute implements Route {
    private static final Logger logger = LoggerFactory.getLogger(GetDsmMedicalRecordRoute.class);
    private MedicalRecordService medicalRecordService;

    public GetDsmMedicalRecordRoute(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @Override
    public Participant handle(Request request, Response response) {
        return TransactionWrapper.withTxn(handle -> {
            logger.info("Starting GetDsmMedicalRecordRoute.handle");
            logger.info("Checking Study and Participant GUIDs");
            String studyGuid = request.params(STUDY_GUID);
            if (studyGuid == null) {
                logger.error("Study GUID not found in request");
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Study GUID is missing"));
            }
            String userGuidOrAltpid = request.params(USER_GUID);
            if (userGuidOrAltpid == null) {
                logger.error("Participant GUID not found in request");
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.MISSING_USER_GUID, "User GUID is missing"));
            }
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                        new ApiError(ErrorCodes.NOT_FOUND, studyGuid + " was not found"));
            }

            // look for the participant by guid or legacy altpid
            DsmStudyParticipantDao dsmStudyParticipantDao = handle.attach(DsmStudyParticipantDao.class);
            DsmStudyParticipant dsmParticipant = dsmStudyParticipantDao
                    .findStudyParticipant(userGuidOrAltpid, studyGuid,
                            Arrays.asList(EnrollmentStatusType.ENROLLED, EnrollmentStatusType.EXITED_AFTER_ENROLLMENT))
                    .orElse(null);
            if (dsmParticipant == null) {
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND,
                        "Participant with identifier " + userGuidOrAltpid + " in study " + studyGuid
                                + " is not in the appropriate state."));
            }

            Participant participant = new Participant();

            medicalRecordService.getDateOfDiagnosis(handle, dsmParticipant.getUserId(), studyDto.getId()).ifPresent(
                    dateValue -> participant.setDateOfDiagnosis(
                            DateTimeFormatUtils.convertDateToSlashSeparatedDsmFormat(dateValue)
                    )
            );

            medicalRecordService.getDateOfBirth(handle, dsmParticipant.getUserId(), studyDto.getId()).ifPresent(
                    dateValue -> participant.setDateOfBirth(
                            DateTimeFormatUtils.convertDateToDashSeparatedDsmFormat(dateValue)
                    )
            );

            participant.setParticipantGUID(userGuidOrAltpid);

            logger.info("Retrieving consent summaries and checking for Blood and Tissue consent");

            // Converting booleans to ints because it's the format DSM expects
            MedicalRecordService.ParticipantConsents consents = medicalRecordService.fetchBloodAndTissueConsents(
                    handle, dsmParticipant.getUserId(), dsmParticipant.getUserGuid(), null, studyDto.getId(), studyGuid);
            int hasConsentedToBloodDraw = consents.hasConsentedToBloodDraw()
                    ? DsmConsentElection.ELECTION_SELECTED.getNumberValue() : DsmConsentElection.ELECTION_NOT_SELECTED.getNumberValue();
            participant.setHasConsentedToBloodDraw(hasConsentedToBloodDraw);
            int hasConsentedToTissueSample = consents.hasConsentedToTissueSample()
                    ? DsmConsentElection.ELECTION_SELECTED.getNumberValue() : DsmConsentElection.ELECTION_NOT_SELECTED.getNumberValue();
            participant.setHasConsentedToTissueSample(hasConsentedToTissueSample);

            logger.info("Retrieving Institutions");
            List<Institution> institutions = handle.attach(JdbiMedicalProvider.class)
                    .getAllByUserGuidStudyGuid(dsmParticipant.getUserGuid(), studyGuid)
                    .stream()
                    .map(dto -> new Institution(dto))
                    .collect(Collectors.toList());

            participant.setInstitutionList(institutions);

            return participant;
        });
    }

    private enum DsmConsentElection {
        ELECTION_NOT_SELECTED(0),
        ELECTION_SELECTED(1);

        private int numberValue;

        DsmConsentElection(int numberValue) {
            this.numberValue = numberValue;
        }

        public int getNumberValue() {
            return numberValue;
        }
    }
}
