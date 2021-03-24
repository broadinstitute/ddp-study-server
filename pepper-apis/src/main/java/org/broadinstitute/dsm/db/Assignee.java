package org.broadinstitute.dsm.db;

import lombok.Data;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class Assignee {

    private static final Logger logger = LoggerFactory.getLogger(Assignee.class);

    private static final String SQL_SELECT_ASSIGNEE = "SELECT user.user_id, user.name, user.email FROM access_user_role_group roleGroup, access_user user, access_role role, ddp_group," +
            " ddp_instance_group realmGroup, ddp_instance realm WHERE roleGroup.user_id = user.user_id AND roleGroup.role_id = role.role_id AND realm.ddp_instance_id = realmGroup.ddp_instance_id" +
            " AND realmGroup.ddp_group_id = ddp_group.group_id AND ddp_group.group_id = roleGroup.group_id AND role.name = \"mr_request\" AND realm.instance_name = ?";

    private final String assigneeId;
    private final String name;
    private final String email;

    public Assignee(String assigneeId, String name, String email){
        this.assigneeId = assigneeId;
        this.name = name;
        this.email = email;
    }

    public static Collection<Assignee> getAssignees(String realm) {
        return getAssigneeMap(realm).values();
    }

    /**
     * Read assignees form assignee table
     * @return List<Assignee>
     */
    public static HashMap<String, Assignee> getAssigneeMap(String realm) {
        HashMap<String, Assignee> assignees = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ASSIGNEE)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString(DBConstants.USER_ID);
                        assignees.put(id, new Assignee(id,
                                rs.getString(DBConstants.NAME),
                                rs.getString(DBConstants.EMAIL)
                        ));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of assignees ", results.resultException);
        }
        logger.info("Found " + assignees.size() + " assignees ");
        return assignees;
    }
}
