package org.broadinstitute.ddp.route;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.UserAnnouncement;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetUserAnnouncementsRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetUserAnnouncementsRoute.class);

    private final I18nContentRenderer renderer;

    public GetUserAnnouncementsRoute(I18nContentRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public List<UserAnnouncement> handle(Request request, Response response) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String participantGuid = request.params(PathParam.USER_GUID);

        LOG.info("Attempting to retrieve announcements for participant {} and study {}", participantGuid, studyGuid);

        String langCode = RouteUtil.getDDPAuth(request).getPreferredLanguage();
        ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);
        LOG.info("Using ddp content style {} and language code {} to render announcement messages", style, langCode);

        return TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            UserDto participantDto = handle.attach(JdbiUser.class).findByUserGuid(participantGuid);
            if (participantDto == null) {
                ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, "Could not find participant with guid " + participantGuid);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            UserAnnouncementDao announcementDao = handle.attach(UserAnnouncementDao.class);

            Set<Long> announcementIds = new HashSet<>();
            List<UserAnnouncement> announcements = announcementDao
                    .findAllForParticipantAndStudy(participantDto.getUserId(), studyDto.getId())
                    .peek(announcement -> announcementIds.add(announcement.getId()))
                    .collect(Collectors.toList());

            LOG.info("Found {} announcements for participant {} and study {}", announcements.size(), participantGuid, studyGuid);

            if (!announcements.isEmpty()) {
                try {
                    long langCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(langCode);
                    renderer.bulkRenderAndApply(handle, announcements, style, langCodeId);
                } catch (NoSuchElementException e) {
                    ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, String.format(
                            "Error while rendering announcement messages for participant %s and study %s",
                            participantGuid, studyGuid));
                    LOG.error(err.getMessage(), e);
                    throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                }

                // Currently, announcements read are automatically deleted.
                int numDeleted = announcementDao.deleteByIds(announcementIds);
                if (numDeleted != announcements.size()) {
                    ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, String.format(
                            "Error while automatically deleting announcements for participant %s and study %s",
                            participantGuid, studyGuid));
                    LOG.error(err.getMessage());
                    throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                } else {
                    LOG.info("Automatically deleted {} announcements for participant {} and study {}",
                            numDeleted, participantGuid, studyGuid);
                }
            }

            return announcements;
        });
    }
}
