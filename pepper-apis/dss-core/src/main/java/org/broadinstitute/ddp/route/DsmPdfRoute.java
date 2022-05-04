package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.io.InputStream;
import javax.servlet.http.HttpServletResponse;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiStudyPdfMapping;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.dsm.StudyPdfMapping;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class DsmPdfRoute implements Route {
    private final PdfService pdfService;
    private final PdfBucketService pdfBucketService;
    private final PdfGenerationService pdfGenerationService;

    abstract PdfMappingType getPdfMappingType();

    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String participantGuidOrAltPid = request.params(PathParam.USER_GUID);
        PdfMappingType pdfMappingType = getPdfMappingType();

        log.info("Attempting to fetch {} pdf for study {} and participant {}", pdfMappingType, studyGuid, participantGuidOrAltPid);

        return TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                log.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            User user = handle.attach(UserDao.class).findUserByGuidOrAltPid(participantGuidOrAltPid).orElse(null);
            if (user == null) {
                String msg = "Could not find participant with GUID or Legacy AltPid " + participantGuidOrAltPid;
                ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, msg);
                log.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            haltIfUserNotEnrolled(handle, user, studyDto, response);

            try {
                StudyPdfMapping studyPdfMapping = handle.attach(JdbiStudyPdfMapping.class)
                        .findByStudyIdAndMappingType(studyDto.getId(), pdfMappingType).orElseThrow(() -> {
                            String msg = "Could not find " + pdfMappingType + " pdf mapping for study with GUID " + studyGuid;
                            ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, msg);
                            log.warn(err.getMessage());
                            return ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                        });

                PdfVersion pdfVersion = pdfService.findConfigVersionForUser(
                        handle, studyPdfMapping.getPdfConfigurationId(), user.getGuid(), studyGuid);

                String umbrellaGuid = handle.attach(JdbiUmbrellaStudy.class).getUmbrellaGuidForStudyGuid(studyGuid);
                String blobName = PdfBucketService.getBlobName(
                        umbrellaGuid,
                        studyGuid,
                        user.getGuid(),
                        studyPdfMapping.getPdfConfigurationName(),
                        pdfVersion.getVersionTag());

                InputStream pdf = pdfBucketService.getPdfFromBucket(blobName).orElse(null);
                if (pdf == null) {
                    log.info("Could not find {} pdf for participant {} using filename {}, generating",
                            pdfMappingType, user.getGuid(), blobName);
                    PdfConfiguration pdfConfig = handle.attach(PdfDao.class).findFullConfig(pdfVersion);
                    pdf = pdfGenerationService.generateFlattenedPdfForConfiguration(
                            pdfConfig,
                            user.getGuid(),
                            handle);

                    pdfBucketService.sendPdfToBucket(blobName, pdf);
                    log.info("Uploaded pdf to bucket {} with filename {}", pdfBucketService.getBucketName(), blobName);
                }

                byte[] pdfBytes = IOUtils.toByteArray(pdf);
                response.type("application/pdf");
                response.header("Content-Disposition", String.format("inline; filename=\"%s.pdf\"", studyPdfMapping.getPdfFileName()));

                HttpServletResponse raw = response.raw();
                raw.getOutputStream().write(pdfBytes);
                raw.getOutputStream().flush();
                raw.getOutputStream().close();
                return raw;
            } catch (IOException | DDPException e) {
                String msg = String.format("Failed to fetch %s pdf for study %s and participant %s",
                        pdfMappingType, studyGuid, participantGuidOrAltPid);
                ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, msg);
                log.error(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
            }
        });
    }

    /**
     * Ensures the user is enrolled (not registered or exited), or halts the execution of the route.
     *
     * @param handle   the database handle
     * @param user     the user
     * @param studyDto the study
     * @param response the route response
     */
    private void haltIfUserNotEnrolled(Handle handle, User user, StudyDto studyDto, Response response) {
        EnrollmentStatusType enrollmentStatusType = handle.attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyIds(user.getId(), studyDto.getId())
                .orElseThrow(() -> {
                    String msg = "Could not find enrollment status for user with GUID " + user.getGuid();
                    ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, msg);
                    log.error(err.getMessage());
                    return ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                });

        if (!enrollmentStatusType.isEnrolled()) {
            String msg = "User " + user.getGuid() + " was not enrolled in study " + studyDto.getGuid();
            ApiError err = new ApiError(ErrorCodes.UNSATISFIED_PRECONDITION, msg);
            log.error(err.getMessage());
            throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
        }
    }
}
