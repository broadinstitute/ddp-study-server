package org.broadinstitute.dsm.db.dao.user;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.lddp.db.SimpleResult;
import spark.utils.StringUtils;

public class UserDao implements Dao<UserDto> {

    public static final String USER_ID = "user_id";
    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String PHONE_NUMBER = "phone_number";
    public static final String IS_ACTIVE = "is_active";
    private static final String SQL_INSERT_USER = "INSERT INTO access_user (name, email, phone_number, is_active) VALUES (?,?,?,?)";
    private static final String SQL_UPDATE_USER =
            "UPDATE access_user SET name = ?, phone_number = ?, is_active = ? WHERE user_id = ?";
    private static final String SQL_DELETE_USER_BY_ID = "DELETE FROM access_user WHERE user_id = ?";
    private static final String SQL_SELECT_USER_BY_EMAIL =
            "SELECT user.user_id, user.name, user.email, user.phone_number, user.is_active FROM access_user user "
                    + "WHERE UPPER(user.email) = ?";
    private static final String SQL_SELECT_USER_BY_ID =
            "SELECT user.user_id, user.name, user.email, user.phone_number, user.is_active FROM access_user user "
                    + "WHERE user.user_id = ?";

    public Optional<UserDto> getUserByEmail(@NonNull String email) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_BY_EMAIL)) {
                stmt.setString(1, email.toUpperCase());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new UserDto(rs.getInt(USER_ID),
                                rs.getString(NAME),
                                rs.getString(EMAIL),
                                rs.getString(PHONE_NUMBER),
                                rs.getInt(IS_ACTIVE));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error getting user by email " + email, results.resultException);
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
                                rs.getString(PHONE_NUMBER),
                                rs.getInt(IS_ACTIVE));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error getting user by id " + userId, results.resultException);
        }
        return Optional.ofNullable((UserDto) results.resultValue);
    }

    @Override
    public int create(UserDto userDto) {
        String email = userDto.getEmailOrThrow();
        if (StringUtils.isBlank(email)) {
            throw new DsmInternalError("Error inserting user: email is blank");
        }
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, userDto.getName().orElse(null));
                stmt.setString(2, email);
                stmt.setString(3, userDto.getPhoneNumber().orElse(null));
                stmt.setInt(4, userDto.getIsActive().orElse(1));
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new DsmInternalError("Error inserting user " + email);
                    }
                    return rs.getInt(1);
                }
            } catch (SQLException ex) {
                throw new DsmInternalError("Error inserting user " + email, ex);
            }
        });
    }

    public static void update(int userId, UserDto userDto) {
        String email = userDto.getEmailOrThrow();
        String errorMsg = "Error updating user " + email;
        int res = inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_USER)) {
                stmt.setString(1, userDto.getName().orElse(null));
                stmt.setString(2, userDto.getPhoneNumber().orElse(null));
                stmt.setInt(3, userDto.getIsActive().orElse(1));
                stmt.setInt(4, userId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new DsmInternalError(errorMsg + " Result count was " + result);
                }
                return result;
            } catch (SQLException ex) {
                throw new DsmInternalError(errorMsg, ex);
            }
        });
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = DaoUtil.deleteById(id, SQL_DELETE_USER_BY_ID);
        if (simpleResult.resultException != null) {
            throw new DsmInternalError("Error deleting user with ID: " + id,
                    simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }
}
