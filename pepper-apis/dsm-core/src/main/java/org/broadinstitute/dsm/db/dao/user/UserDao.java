package org.broadinstitute.dsm.db.dao.user;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.lddp.db.SimpleResult;

public class UserDao implements Dao<UserDto> {

    public static final String USER_ID = "user_id";
    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String PHONE_NUMBER = "phone_number";
    private static final String SQL_INSERT_USER = "INSERT INTO access_user (name, email, phone_number, is_active) VALUES (?,?,?,?)";
    private static final String SQL_DELETE_USER_BY_ID = "DELETE FROM access_user WHERE user_id = ?";
    private static final String SQL_SELECT_USER_BY_EMAIL =
            "SELECT user.user_id, user.name, user.email, user.phone_number FROM access_user user WHERE user.email = ?";
    private static final String SQL_SELECT_USER_BY_ID =
            "SELECT user.user_id, user.name, user.email, user.phone_number FROM access_user user WHERE user.user_id = ?";

    public Optional<UserDto> getUserByEmail(@NonNull String email) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_BY_EMAIL)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new UserDto(rs.getInt(USER_ID),
                                rs.getString(NAME),
                                rs.getString(EMAIL),
                                rs.getString(PHONE_NUMBER));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms ", results.resultException);
        }
        return Optional.ofNullable((UserDto) results.resultValue);
    }

    @Override
    public Optional<UserDto> get(long userId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_BY_ID)) {
                stmt.setLong(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new UserDto(rs.getInt(USER_ID),
                                rs.getString(NAME),
                                rs.getString(EMAIL),
                                rs.getString(PHONE_NUMBER));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms ", results.resultException);
        }
        return Optional.ofNullable((UserDto) results.resultValue);
    }

    @Override
    public int create(UserDto userDto) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_USER, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, userDto.getName().orElse(""));
                stmt.setString(2, userDto.getEmail().orElse(""));
                stmt.setString(3, userDto.getPhoneNumber().orElse(""));
                stmt.setInt(4, userDto.getIsActive().orElse(0));
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        execResult.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new DsmInternalError("Error inserting user " + userDto.getEmail(), results.resultException);
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
            } catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new DsmInternalError("Error deleting user with ID " + id, results.resultException);
        }
        return (int) results.resultValue;
    }
}
