package org.broadinstitute.ddp.db.dao;

import java.time.Instant;


import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiSendGridEvent extends SqlObject {

    /**
     * Insert SendGrid event
     */
    @SqlUpdate("insert into sendgrid_event "
            + "(email,timestamp,event_type,url,ip,sg_event_id,sg_message_id,response,reason,status,attempt) "
            + "values(:email,:timestamp,:eventType,:url,:ip,:sgEventId,:sgMessageId,:response,:reason,:status,:attempt)"
    )
    @GetGeneratedKeys
    long inserSendGridEvent(
            @Bind("email") String email,
            @Bind("timestamp") Instant timestamp,
            @Bind("eventType") String eventType,
            @Bind("url") String url,
            @Bind("ip") String ip,
            @Bind("sgEventId") String sgEventId,
            @Bind("sgMessageId") String sgMessageId,
            @Bind("response") String response,
            @Bind("reason") String reason,
            @Bind("status") String status,
            @Bind("attempt") int attempt
    );
}
