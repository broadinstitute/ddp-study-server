package org.broadinstitute.dsm.db;

import lombok.Getter;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Getter
public class AbstractionGroup {

    private static final Logger logger = LoggerFactory.getLogger(AbstractionGroup.class);

    private static final String SQL_INSERT_FORM_GROUP = "INSERT INTO medical_record_abstraction_group SET display_name = ?, ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?), order_number = ?";
    private static final String SQL_DELETE_FORM_GROUP = "UPDATE medical_record_abstraction_group SET deleted = 1 WHERE medical_record_abstraction_group_id = ?";
    private static final String SQL_UPDATE_FORM_GROUP = "UPDATE medical_record_abstraction_group SET order_number = ? WHERE medical_record_abstraction_group_id = ?";

    private final int abstractionGroupId;
    private final String displayName;
    private final int orderNumber;
    private List<AbstractionField> fields;

    private boolean deleted;
    private boolean newAdded;
    private boolean changed;

    public AbstractionGroup(int abstractionGroupId, String displayName, int orderNumber) {
        this.abstractionGroupId = abstractionGroupId;
        this.displayName = displayName;
        this.orderNumber = orderNumber;
        this.fields = new ArrayList<>();
    }

    public static AbstractionGroup getGroup(@NonNull ResultSet rs) throws SQLException {
        AbstractionGroup group = new AbstractionGroup(rs.getInt(DBConstants.MEDICAL_RECORD_ABSTRACTION_GROUP_ID),
                rs.getString("cgroup." + DBConstants.DISPLAY_NAME),
                rs.getInt("cgroup." + DBConstants.ORDER_NUMBER));
        return group;
    }

    public void addField(AbstractionField field) {
        if (fields != null) {
            fields.add(field);
        }
    }

    public static void saveFormControls(@NonNull String realm, @NonNull AbstractionGroup[] receivedAbstractionGroups) {
        for (AbstractionGroup group : receivedAbstractionGroups) {
            if (group.getFields() != null && !group.getFields().isEmpty()) {
                if (group.isNewAdded()) {
                    int abstractionGroupId = AbstractionGroup.insertNewGroup(realm, group);
                    if (group.getFields() != null) {
                        for (AbstractionField field : group.getFields()) {
                            AbstractionField.insertNewField(realm, abstractionGroupId, field);
                        }
                    }
                }
                else if (group.isDeleted()) {
                    AbstractionGroup.deleteView(group.getAbstractionGroupId());
                    if (group.getFields() != null) {
                        for (AbstractionField field : group.getFields()) {
                            AbstractionField.deleteField(field);
                        }
                    }
                }
                else {
                    //for group only the order_number can be changed
                    AbstractionGroup.updateOrderNumber(group);
                    if (group.getFields() != null) {
                        for (AbstractionField field : group.getFields()) {
                            if (field.isNewAdded()) {
                                AbstractionField.insertNewField(realm, group.getAbstractionGroupId(), field);
                            }
                            else if (field.isDeleted()) {
                                AbstractionField.deleteField(field);
                            }
                            else {
                                AbstractionField.updateField(field);
                            }
                        }
                    }
                }
            }
        }
    }

    public static int insertNewGroup(@NonNull String realm, @NonNull AbstractionGroup abstractionGroup) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_FORM_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, abstractionGroup.getDisplayName());
                stmt.setString(2, realm);
                stmt.setInt(3, abstractionGroup.getOrderNumber());
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            dbVals.resultValue = rs.getInt(1);
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error getting groupId of new abstraction group ", e);
                    }
                }
                else {
                    throw new RuntimeException("Error adding new abstraction group. It was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new abstraction group", results.resultException);
        }
        else {
            return (int) results.resultValue;
        }
    }

    public static void deleteView(@NonNull int groupId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_FORM_GROUP)) {
                stmt.setInt(1, groupId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error deleting abstraction group. Query changed " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error deleting abstraction group", results.resultException);
        }
    }

    public static void updateOrderNumber(@NonNull AbstractionGroup abstractionGroup) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_FORM_GROUP)) {
                stmt.setInt(1, abstractionGroup.getOrderNumber());
                stmt.setInt(2, abstractionGroup.getAbstractionGroupId());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating abstraction group. Query changed " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating abstraction group", results.resultException);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractionGroup that = (AbstractionGroup) o;
        return Objects.equals(displayName, that.displayName);
    }
}
