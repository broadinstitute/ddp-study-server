package org.broadinstitute.dsm.db;

import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class EditParticipantMessage {

    private static final Logger logger = LoggerFactory.getLogger(EditParticipantMessage.class);

    private static final String SQL_SELECT_MESSAGE_AND_STATUS =
            "SELECT " +
                "message_id, message_status, received_message " +
            "FROM " +
                "message " +
            "WHERE " +
                "user_id = ? " +
            "ORDER BY published_at DESC " +
            "LIMIT 1";

    private static final String SQL_INSERT_MESSAGE =
            "INSERT INTO " +
                    "message " +
                    "(user_id, message_status, published_at) " +
            "VALUES " +
                    "(?, ?, ?)";

    private static final String SQL_UPDATE_MESSAGE =
            "UPDATE " +
                    "message " +
            "SET " +
                    "message_status = ?, received_message = ?, received_at = ? " +
            "WHERE " +
                    "user_id = ? " +
            "ORDER BY published_at DESC " +
            "LIMIT 1";

    private static final String SQL_UPDATE_MESSAGE_STATUS =
            "UPDATE " +
                    "message " +
            "SET " +
                    "message_status = ? " +
            "WHERE " +
                    "message_id = ? ";

    private int messageId;
    private int userId;
    private String messageStatus;
    private long published_at;
    private String received_message;
    private long received_at;

    public EditParticipantMessage(int userId, String messageStatus, long published_at) {
        this.userId = userId;
        this.messageStatus = messageStatus;
        this.published_at = published_at;
    }

    public EditParticipantMessage(int messageId, String messageStatus, String received_message) {
        this.messageId = messageId;
        this.messageStatus = messageStatus;
        this.received_message = received_message;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getMessageStatus() {
        return messageStatus;
    }

    public void setMessageStatus(String messageStatus) {
        this.messageStatus = messageStatus;
    }

    public long getPublished_at() {
        return published_at;
    }

    public void setPublished_at(long published_at) {
        this.published_at = published_at;
    }

    public String getReceived_message() {
        return received_message;
    }

    public void setReceived_message(String received_message) {
        this.received_message = received_message;
    }

    public long getReceived_at() {
        return received_at;
    }

    public void setReceived_at(long received_at) {
        this.received_at = received_at;
    }

    public static EditParticipantMessage getMessageWithStatus(int userId) {
        List<EditParticipantMessage> messagesWithStatus = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();

            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_MESSAGE_AND_STATUS)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messagesWithStatus.add(new EditParticipantMessage(rs.getInt(DBConstants.MESSAGE_ID),
                                rs.getString(DBConstants.MESSAGE_STATUS),
                                rs.getString(DBConstants.RECEIVED_MESSAGE)));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting message status ", results.resultException);
        }

        return messagesWithStatus.get(0);
    }

    public static void insertMessage(@NonNull EditParticipantMessage message) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_MESSAGE)) {
                stmt.setInt(1, message.getUserId());
                stmt.setString(2, message.getMessageStatus());
                stmt.setLong(3, message.getPublished_at());
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Added new message ");
                }
                else {
                    throw new RuntimeException("Error adding new message, it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error inserting message for the user with user ID: "
                    + message.getUserId(), results.resultException);
        }
    }

    public static void updateMessage(@NonNull int userId, @NonNull String messageStatus, @NonNull String message, @NonNull long received_at) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_MESSAGE)) {
                stmt.setString(1, messageStatus);
                stmt.setString(2, message);
                stmt.setLong(3, received_at);
                stmt.setInt(4, userId);

                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updating message status of user with id: " + userId);
                }
                else {
                    throw new RuntimeException("Error updating message status of user with " + userId + ". it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error updating message status of user with: " + userId, results.resultException);
        }
    }

    public static void updateMessageStatusById(@NonNull int messageId, @NonNull String messageStatus) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_MESSAGE_STATUS)) {
                stmt.setString(1, messageStatus);
                stmt.setInt(2, messageId);

                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updating status of message by id: " + messageId);
                }
                else {
                    throw new RuntimeException("Error updating status of message with id: " + messageId + ". it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error updating status of message with id: " + messageId, results.resultException);
        }
    }

}
