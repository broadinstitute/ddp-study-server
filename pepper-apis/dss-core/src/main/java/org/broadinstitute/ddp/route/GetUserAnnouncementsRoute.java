package org.broadinstitute.ddp.route;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserAnnouncement;
import org.broadinstitute.ddp.security.DDPAuth;
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
        String userGuid = request.params(PathParam.USER_GUID);
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);

        LOG.info("Attempting to retrieve announcements for user {} and study {}", userGuid, studyGuid);

        ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);

        return TransactionWrapper.withTxn(handle -> {
            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            String langCode = preferredUserLanguage.getIsoCode();

            LOG.info("Using ddp content style {} and language code {} to render announcement messages", style, langCode);
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            User user = handle.attach(UserDao.class).findUserByGuid(userGuid).orElse(null);
            if (user == null) {
                ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, "Could not find user with guid " + userGuid);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            UserAnnouncementDao announcementDao = handle.attach(UserAnnouncementDao.class);

            List<UserAnnouncement> announcements;
            try (Stream<UserAnnouncement> annStream = announcementDao
                    .findAllForUserAndStudy(user.getId(), studyDto.getId())) {
                announcements = annStream.collect(Collectors.toList());
            }

            LOG.info("Found {} announcements for user {} and study {}", announcements.size(), userGuid, studyGuid);

            if (!announcements.isEmpty()) {
                try {
                    // Render messages using current time to get latest templates.
                    long timestamp = Instant.now().toEpochMilli();
                    long langCodeId = preferredUserLanguage.getId();
                    renderer.bulkRenderAndApply(handle, announcements, style, langCodeId, timestamp);
                } catch (NoSuchElementException e) {
                    ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, String.format(
                            "Error while rendering announcement messages for user %s and study %s",
                            userGuid, studyGuid));
                    LOG.error(err.getMessage(), e);
                    throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                }

                Set<Long> transientAnnouncementIds = announcements.stream()
                        .filter(ann -> !ann.isPermanent())
                        .map(UserAnnouncement::getId)
                        .collect(Collectors.toSet());

                int numDeleted = announcementDao.deleteByIds(transientAnnouncementIds);
                if (numDeleted != transientAnnouncementIds.size()) {
                    ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, String.format(
                            "Error while deleting non-permanent announcements for user %s and study %s",
                            userGuid, studyGuid));
                    LOG.error(err.getMessage());
                    throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                } else {
                    LOG.info("Deleted {} non-permanent announcements for user {} and study {}",
                            numDeleted, userGuid, studyGuid);
                }
            }

            return announcements;
        });
    }
}
