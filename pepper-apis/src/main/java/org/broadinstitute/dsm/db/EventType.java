package org.broadinstitute.dsm.db;

import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class EventType {

    private static final Logger logger = LoggerFactory.getLogger(EventType.class);

    private static final String SQL_SELECT_EVENT_TYPE = "SELECT event_name, event_description FROM event_type ty, ddp_instance realm WHERE ty.ddp_instance_id = realm.ddp_instance_id AND realm.instance_name = ?";

    private final String name;
    private final String description;

    public EventType(String name, String description){
        this.name = name;
        this.description = description;
    }

    public static Collection<EventType> getEventTypes(@NonNull String realm) {
        ArrayList<EventType> eventTypes = new ArrayList();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_EVENT_TYPE)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        eventTypes.add(new EventType(rs.getString(DBConstants.EVENT_NAME),
                                rs.getString(DBConstants.EVENT_DESCRIPTION)));
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get event types for " + realm, results.resultException);
        }
        return eventTypes;
    }
}
