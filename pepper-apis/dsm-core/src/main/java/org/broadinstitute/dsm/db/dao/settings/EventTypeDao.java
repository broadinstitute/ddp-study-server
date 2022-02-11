package org.broadinstitute.dsm.db.dao.settings;

import com.sun.istack.NotNull;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.settings.EventTypeDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class EventTypeDao implements Dao<EventTypeDto> {

    private static final Logger logger = LoggerFactory.getLogger(EventTypeDao.class);

    public static final String EVENT = "PARTICIPANT_EVENT";
    public static final String RECEIVED = "RECEIVED";
    public static final String SENT = "SENT";

    private static String GET_EVENT_TYPE = "SELECT " +
            "eve.event_name, eve.event_type, " +
            "realm.ddp_instance_id, realm.instance_name, realm.base_url, realm.auth0_token " +
            "FROM " +
            "event_type eve, " +
            "ddp_instance realm ";

    private static String GET_EVENT_TYPE_BY_INSTANCE_NAME =  GET_EVENT_TYPE +
            "WHERE eve.ddp_instance_id = realm.ddp_instance_id " +
            "AND realm.instance_name = ?";

    private static String GET_EVENT_TYPE_BY_EVENT_NAME_AND_INSTANCE_ID =  GET_EVENT_TYPE +
            "WHERE eve.ddp_instance_id = realm.ddp_instance_id " +
            "AND eve.event_name = ? " +
            "AND realm.ddp_instance_id = ?";

    public static final String EVENT_NAME = "event_name";
    public static final String EVENT_TYPE = "event_type";

    @Override
    public int create(EventTypeDto eventTypeDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<EventTypeDto> get(long id) {
        return Optional.empty();
    }

    public List<EventTypeDto> getEventTypeByInstanceName(@NonNull String instanceName) {
        List<EventTypeDto> eventTypeList = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_EVENT_TYPE_BY_INSTANCE_NAME)) {
                stmt.setString(1, instanceName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        eventTypeList.add(new EventTypeDto.Builder(rs.getString(DBConstants.DDP_INSTANCE_ID))
                                .withEventName(rs.getString(EVENT_NAME))
                                .withEventType(rs.getString(EVENT_TYPE))
                                .withInstanceName(rs.getString(DBConstants.INSTANCE_NAME))
                                .withBaseUrl(rs.getString(DBConstants.BASE_URL))
                                .withAuth0Token(rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN))
                                .build()
                        );
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting event types ", results.resultException);
        }
        return eventTypeList;
    }

    public Optional<EventTypeDto> getEventTypeByEventTypeAndInstanceId(@NotNull String eventType, @NonNull String instanceId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(GET_EVENT_TYPE_BY_EVENT_NAME_AND_INSTANCE_ID, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, eventType);
                stmt.setString(2, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.beforeFirst();
                    if (count == 1) {
                        if (rs.next()) { //if row is 0 the ddp/kit type combination does not trigger a participant event
                            dbVals.resultValue = new EventTypeDto.Builder(rs.getString(DBConstants.DDP_INSTANCE_ID))
                                    .withEventName(rs.getString(EVENT_NAME))
                                    .withEventType(rs.getString(EVENT_TYPE))
                                    .withInstanceName(rs.getString(DBConstants.INSTANCE_NAME))
                                    .withBaseUrl(rs.getString(DBConstants.BASE_URL))
                                    .withAuth0Token(rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN))
                                    .build();
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get exited participants for " + instanceId, results.resultException);
        }
        return Optional.ofNullable((EventTypeDto) results.resultValue);
    }
}
