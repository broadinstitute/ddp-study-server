package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.exception.DMLException;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * Created by ebaker on 5/1/17.
 */
@Data
public class Contact {
    private static final Logger logger = LoggerFactory.getLogger(Contact.class);

    private static final String LOG_PREFIX = "CONTACT - ";

    public static final String NO_CONTACTS_ADDED = "No contacts were added.";
    public static final String NO_CONTACTS_REMOVED = "No contacts were removed.";

    private static final String SQL_CONTACT_EXISTS = "SELECT COUNT(*) FROM CONTACT WHERE CONT_EMAIL = ?";
    private static final String SQL_INSERT_CONTACT = "INSERT INTO CONTACT (CONT_FIRST_NAME, CONT_LAST_NAME, CONT_EMAIL, CONT_DATE_CREATED) VALUES (?, ?, ?, ?)";
    private static final String SQL_DELETE_CONTACT = "DELETE FROM CONTACT WHERE CONT_EMAIL = ?";
    private static final String SQL_CONTACT = "SELECT CONT_FIRST_NAME, CONT_LAST_NAME, CONT_EMAIL, CONT_DATE_CREATED FROM CONTACT WHERE CONT_EMAIL = ?";
    private static final String SQL_ALL_CONTACTS = "SELECT CONT_FIRST_NAME, CONT_LAST_NAME, CONT_EMAIL, CONT_DATE_CREATED FROM CONTACT ORDER BY CONT_LAST_NAME, CONT_FIRST_NAME";

    private String firstName;
    private String lastName;
    private String email;
    private Long dateCreated;

    public Contact() {
    }

    public Contact(@NonNull String firstName, @NonNull String lastName, @NonNull String email, Long dateCreated) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateCreated = dateCreated;
    }

    /**
     * Populates a contact using their information from the database.
     *
     * @param email contact's unique identifier
     * @return will return null if contact is not found in db (it will not throw an exception)
     */
    public static Contact getContactFromDB(@NonNull String email) {
        logger.info(LOG_PREFIX + "Retrieving contact...");

        ArrayList<Contact> contacts = getContactsFromDB(email, SQL_CONTACT);

        if (contacts.isEmpty()) {
            return null;
        }
        else if (contacts.size() > 1) {
            throw new DMLException("Too many contacts found with email address: " + email);
        }
        else {
            return contacts.get(0);
        }
    }

    public static ArrayList<Contact> getAllContactsFromDB() {
        logger.info(LOG_PREFIX + "Retrieving all contacts...");
        return getContactsFromDB(null, SQL_ALL_CONTACTS);
    }

    private static ArrayList<Contact> getContactsFromDB(String email, String sql) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            ArrayList<Contact> contacts = new ArrayList<>();
            Long dateCreated;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                if (email != null) stmt.setString(1, email);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dateCreated = (rs.getObject(4) != null) ? rs.getLong(4) : null;
                        contacts.add(new Contact(rs.getString(1), rs.getString(2), rs.getString(3), dateCreated));
                    }
                }
                dbVals.resultValue = contacts;
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException("An error occurred while retrieving contact(s).", results.resultException);
        }

        return (ArrayList)results.resultValue;
    }

    /**
     * Check the database to see if the person is already on the mailing list.
     *
     * @return true if the contact is already on the mailing list
     */
    public static boolean contactExistsInMailingList(@NonNull String email)
    {
        logger.info(LOG_PREFIX + "Checking for contact...");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_CONTACT_EXISTS);)
            {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery();)
                {
                    while (rs.next())
                    {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            }
            catch (Exception ex)
            {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null)
        {
            throw new DMLException("An error occurred while attempting to find a contact.", results.resultException);
        }

        return ((Integer)results.resultValue > 0);
    }

    /**
     * Inserts a new mailing list contact into the database.
     */
    public static void addContactToMailingList(@NonNull String firstName, @NonNull String lastName, @NonNull String email)
    {
        logger.info(LOG_PREFIX + "Adding contact...");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_CONTACT);)
            {
                stmt.setString(1, firstName);
                stmt.setString(2, lastName);
                stmt.setString(3, email);
                stmt.setLong(4, Utility.getCurrentEpoch());
                dbVals.resultValue = stmt.executeUpdate();
            }
            catch (Exception ex)
            {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if ((results.resultException != null)||((Integer)results.resultValue != 1))
        {
            throw new DMLException(NO_CONTACTS_ADDED, results.resultException);
        }
    }

    /**
     * Deletes a contact from the database using their email as the unique identifier.
     */
    public static void removeContactFromMailingList(@NonNull String email)
    {
        logger.info(LOG_PREFIX + "Removing contact...");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_CONTACT);)
            {
                stmt.setString(1, email);
                dbVals.resultValue = stmt.executeUpdate();
            }
            catch (Exception ex)
            {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if ((results.resultException != null)||((Integer)results.resultValue != 1))
        {
            throw new DMLException(NO_CONTACTS_REMOVED, results.resultException);
        }
    }
}
