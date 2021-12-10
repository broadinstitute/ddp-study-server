package org.broadinstitute.lddp.datstat;

import lombok.NonNull;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.exception.DMLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * This class handles DatStat-related stuff that needs to happen in the DDP database.
 */
public class DatStatDbHelper
{
    private static final Logger logger = LoggerFactory.getLogger(DatStatDbHelper.class);

    private static final String LOG_PREFIX = "DATSTAT CLIENT:DB - ";
    private static final String NO_BOOKMARKS_UPDATED = "No bookmarks were updated.";

    private static final String SQL_SURVEY_BOOKMARKS = "SELECT SURVEY_NAME, LAST_SENT FROM BOOKMARK";
    private static final String SQL_UPDATE_SURVEY_BOOKMARK = "UPDATE BOOKMARK SET LAST_SENT = ? WHERE SURVEY_NAME = ?";


    /**
     * Retrieves all the survey bookmarks from the database in one call.
     */
    public static Map<String, Integer> getAllSurveyBookmarks()
    {
        logger.info(LOG_PREFIX + "Retrieving all survey bookmarks...");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SURVEY_BOOKMARKS))
            {
                try (ResultSet rs = stmt.executeQuery())
                {
                    while (rs.next())
                    {
                        //for each survey we find we will add the survey name and current bookmark to our hashmap...
                        ((Map<String, Integer>)dbVals.resultValue).put(rs.getString(1), rs.getInt(2));
                    }
                }
            }
            catch (Exception ex)
            {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null)
        {
            throw new DMLException("An error occurred while attempting to retrieve survey bookmarks.", results.resultException);
        }

        return (Map<String, Integer>)results.resultValue;
    }

    /**
     * Updates the bookmark for a given survey.
     */
    public static void updateSurveyBookmark(@NonNull String surveyName, @NonNull int bookmark)
    {
        logger.info(LOG_PREFIX + "About to update survey bookmarks for " + surveyName + "...");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_SURVEY_BOOKMARK))
            {
                stmt.setInt(1, bookmark);
                stmt.setString(2, surveyName);
                dbVals.resultValue = stmt.executeUpdate();
            }
            catch (Exception ex)
            {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if ((results.resultException != null)||((Integer)results.resultValue != 1))
        {
            throw new DMLException(NO_BOOKMARKS_UPDATED, results.resultException);
        }
    }
}
