package org.broadinstitute.dsm.db.dao.user;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.User;
import org.broadinstitute.dsm.db.dao.Dao;

public class UserDao implements Dao<User> {

    private static final String SQL_INSERT_USER = "INSERT INTO access_user (name, email) VALUES (?,?)";
    private static final String SQL_DELETE_USER_BY_ID = "DELETE FROM access_user WHERE user_id = ?";

    @Override
    public int create(User user) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_USER, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, user.getName());
                stmt.setString(2, user.getEmail());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        execResult.resultValue = rs.getInt(1);
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error inserting user with "
                    + user.getEmail(), results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_USER_BY_ID)) {
                stmt.setInt(1, id);
                execResult.resultValue = stmt.executeUpdate();
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error deleting user with "
                    + id, results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public Optional<User> get(long id) {
        return Optional.empty();
    }
}
