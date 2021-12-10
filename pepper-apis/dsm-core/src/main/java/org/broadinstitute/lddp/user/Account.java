package org.broadinstitute.lddp.user;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.exception.DMLException;
import org.broadinstitute.lddp.util.CheckValidity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * Used for application accounts stored in the DB.
 */
//todo add code for creating accounts/roles
@Data
public class Account implements CheckValidity, BasicUser
{
    private static final Logger logger = LoggerFactory.getLogger(Account.class);

    private static final String SQL_ACCOUNT_ROLES = "SELECT R.ROLE_NAME FROM \n" +
            "ACCOUNT A\n" +
            "INNER JOIN ACCOUNT_ROLE AR ON A.ACCT_ID = AR.ACCT_ID\n" +
            "INNER JOIN ROLE R ON AR.ROLE_ID = R.ROLE_ID\n" +
            "WHERE A.ACCT_EMAIL = ?";

    private String firstName = "";
    private String lastName= "";
    private String email= "";
    private String language = "";

    public boolean isValid()
    {
        return ((!firstName.isEmpty())&&(!lastName.isEmpty())&&(!email.isEmpty()));
    }

    public Account()
    {
    }

    public Account(@NonNull String firstName, @NonNull String lastName, @NonNull String email)
    {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public static Collection<String> getRolesFromDB(@NonNull String email)
    {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            ArrayList<String> roles = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_ACCOUNT_ROLES))
            {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery())
                {
                    while (rs.next())
                    {
                        roles.add(rs.getString(1));
                    }
                }
                dbVals.resultValue = roles;
            }
            catch (Exception ex)
            {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null)
        {
            throw new DMLException("An error occurred while getting roles for account.", results.resultException);
        }

        return (Collection<String>)results.resultValue;
    }
}
