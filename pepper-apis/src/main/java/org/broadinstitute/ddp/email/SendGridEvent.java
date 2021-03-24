package org.broadinstitute.ddp.email;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class SendGridEvent {

    private static final Logger logger = LoggerFactory.getLogger(SendGridEvent.class);

    private static final String SQL_SELECT = "SELECT SGE_ID, SGE_SOURCE, SGE_DATA, SGE_DATE_CREATED FROM SENDGRID_EVENT";
    public static final String SELECT_BY_SOURCE = " WHERE SGE_SOURCE =  \'%1\' ";

    private static final String SMTP_ID_SENDGRID = "smtp-id";
    private static final String SMTP_ID = "smtp_id";

    public static final String PROD_ENV = "PROD";

    private String SGE_ID;
    private String SGE_SOURCE;
    private SendGridEventData SGE_DATA;
    private long SGE_DATE_CREATED;

    public SendGridEvent(String SGE_ID, String SGE_SOURCE, SendGridEventData SGE_DATA, long SGE_DATE_CREATED) {
        this.SGE_ID = SGE_ID;
        this.SGE_SOURCE = SGE_SOURCE;
        this.SGE_DATA = SGE_DATA;
        this.SGE_DATE_CREATED = SGE_DATE_CREATED;
    }

    public String getSGE_ID() {
        return SGE_ID;
    }

    public String getSGE_SOURCE() {
        return SGE_SOURCE;
    }

    public SendGridEventData getSGE_DATA() {
        return SGE_DATA;
    }

    public long getSGE_DATE_CREATED() {
        return SGE_DATE_CREATED;
    }

    /**
     * Get all event data from eel db for the given source
     * table SENDGRID_EVENT
     */
    public static List<SendGridEvent> getEELData(@NonNull String transactionWrapperSourceName, String source) {
        List<SendGridEvent> sendGridEvent = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String query = SQL_SELECT;
            if (StringUtils.isNotBlank(source)) {
                query += SELECT_BY_SOURCE.replace("%1", source);
            }
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String sgeData = rs.getString(3).replace(SMTP_ID_SENDGRID, SMTP_ID);
                        SendGridEventData data = new Gson().fromJson(sgeData, SendGridEventData.class);
                        if (PROD_ENV.equals(data.getDdp_env_type())) {
                            sendGridEvent.add(new SendGridEvent(
                                    rs.getString(1),
                                    rs.getString(2),
                                    data,
                                    rs.getLong(4)
                            ));
                        }
                    }
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        }, transactionWrapperSourceName);

        if (results.resultException != null) {
            throw new RuntimeException("Error getting data from eel db ", results.resultException);
        }
        logger.info("Found " + sendGridEvent.size() + " eel event data for source " + source);
        return sendGridEvent;
    }
}