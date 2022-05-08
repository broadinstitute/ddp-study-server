package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Data;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class SurveyTrigger {

    private static final Logger logger = LoggerFactory.getLogger(SurveyTrigger.class);

    private static final String SQL_SELECT_SURVEY_TRIGGER =
            "SELECT st.survey_trigger_id, st.note, st.created_date, st.created_by "
                    + "FROM ddp_survey_trigger st;";

    private String surveyTriggerId;
    private String reason;
    private String user;
    private long triggeredDate;
    private long createdBy;

    public SurveyTrigger(String surveyTriggerId, String reason, String user, long triggeredDate, long createdBy) {
        this.surveyTriggerId = surveyTriggerId;
        this.reason = reason;
        this.user = user;
        this.triggeredDate = triggeredDate;
        this.createdBy = createdBy;
    }

    public static Map<String, SurveyTrigger> getSurveyTriggers() {
        Map<String, SurveyTrigger> surveyTriggers = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SURVEY_TRIGGER)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString(DBConstants.SURVEY_TRIGGER_ID);
                        surveyTriggers.put(id, new SurveyTrigger(id,
                                rs.getString(DBConstants.NOTE),
                                null,
                                rs.getLong(DBConstants.CREATED_DATE),
                                rs.getLong(DBConstants.CREATED_BY)
                        ));
                    }
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of survey triggers ", results.resultException);
        }
        logger.info("Found " + surveyTriggers.size() + " survey triggers ");
        getUserNames(surveyTriggers);
        return surveyTriggers;
    }

    private static void getUserNames(Map<String, SurveyTrigger> surveyTriggers) {
        UserDao userRoleDao = new UserDao();
        List<UserDto> userList = userRoleDao.getAllDSMUsers();
        Optional<BookmarkDto> maybeUserIdBookmark = new BookmarkDao().getBookmarkByInstance("FIRST_DSM_USER_ID");
        maybeUserIdBookmark.orElseThrow();
        Long firstNewUserId = maybeUserIdBookmark.get().getValue();
        for (String key : surveyTriggers.keySet()) {
            SurveyTrigger surveyTrigger = surveyTriggers.get(key);
            long userId = surveyTrigger.getCreatedBy();
            boolean isLegacy = userId < firstNewUserId;
            if (isLegacy) {
                userList.stream().filter(user -> user.getDsmLegacyId() == userId).findAny()
                        .ifPresent(u -> surveyTrigger.user = u.getName().get());
            } else {
                userList.stream().filter(user -> user.getUserId() == userId).findAny()
                        .ifPresent(u -> surveyTrigger.user = u.getName().get());
            }
        }
    }
}
