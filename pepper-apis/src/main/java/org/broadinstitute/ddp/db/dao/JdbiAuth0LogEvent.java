package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Optional;


import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiAuth0LogEvent extends SqlObject {

    /**
     * Insert Auth0 log event
     */
    @SqlUpdate("insert into auth0_log_event "
            + "(tenant,auth0_log_event_code_id,auth0_log_event_created_at,log_id,client_id,connection_id,"
            + "auth0_log_event_user_id,user_agent,ip,email,data) "
            + "values(:tenant,"
            + "(select auth0_log_event_code_id from auth0_log_event_code where code = :type),"
            + ":date,:logId,:clientId,:connectionId,:userId,:userAgent,:ip,:email,:data)"
    )
    @GetGeneratedKeys
    long insertAuth0LogEvent(
            @Bind("tenant") String tenant,
            @Bind("type") String type,
            @Bind("date") Instant date,
            @Bind("logId") String logId,
            @Bind("clientId") String clientId,
            @Bind("connectionId") String connectionId,
            @Bind("userId") String userId,
            @Bind("userAgent") String userAgent,
            @Bind("ip") String ip,
            @Bind("email") String email,
            @Bind("data") String data
    );

    @SqlQuery("select c.* from auth0_log_event_code c where c.code=:type")
    @RegisterRowMapper(Auth0LogEvent.Auth0LogEventCodeDto.Auth0LogEventCodeDtoMapper.class)
    Optional<Auth0LogEvent.Auth0LogEventCodeDto> findAuth0LogEventCodeByType(@Bind("type") String type);
}
