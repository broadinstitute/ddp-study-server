package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.model.event.MessageDestination;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiMessageDestination extends SqlObject {

    @SqlQuery("select message_destination_id from message_destination where gcp_topic = :gcpTopic")
    long findByTopic(@Bind("gcpTopic") String gcpTopic);

    default long findByTopic(MessageDestination messageDestination) {
        return findByTopic(messageDestination.name());
    }

    @SqlQuery("select gcp_topic from message_destination")
    List<String> getAllTopics();

}
