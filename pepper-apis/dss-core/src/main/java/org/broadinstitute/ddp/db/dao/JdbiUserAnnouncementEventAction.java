package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUserAnnouncementEventAction extends SqlObject {

    @SqlUpdate("insert into user_announcement_event_action (event_action_id, message_template_id, is_permanent, create_for_proxies)"
            + " values (:actionId, :msgTemplateId, :permanent, :createForProxies)")
    int insert(@Bind("actionId") long eventActionId,
               @Bind("msgTemplateId") long msgTemplateId,
               @Bind("permanent") boolean isPermanent,
               @Bind("createForProxies") boolean createForProxies);

    @SqlUpdate("delete from user_announcement_event_action where event_action_id = :actionId")
    int deleteById(@Bind("actionId") long eventActionId);
}
