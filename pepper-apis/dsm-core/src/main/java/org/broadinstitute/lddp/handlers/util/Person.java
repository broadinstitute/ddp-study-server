package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.lddp.user.BasicUser;
import org.broadinstitute.lddp.util.CheckValidity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 /**
 * Used by the personrequesthandler.
 */
@Data
public class Person implements CheckValidity, BasicUser
{
    private static final Logger logger = LoggerFactory.getLogger(Person.class);

    private static final String LOG_PREFIX = "PERSON - ";

    public enum PersonType
    {
        PARTICIPANT, CONTACT, LOVED_ONE
    }

    private String firstName = "";
    private String lastName= "";
    private String email= "";
    private String confirmEmail = "";
    private PersonType personType = null;
    private String language = "";
    private String password = "";

    public boolean isValid()
    {
        return (!firstName.isEmpty() && !lastName.isEmpty() && !email.isEmpty() && !confirmEmail.isEmpty() && email.equals(confirmEmail) && personType != null);
    }

    public Person()
    {

    }

    public Person(@NonNull String firstName, @NonNull String lastName)
    {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Person(@NonNull String firstName, @NonNull String lastName, @NonNull String email, @NonNull PersonType personType)
    {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.personType = personType;
    }

    public Person(@NonNull String firstName, @NonNull String lastName, @NonNull String email, @NonNull PersonType personType,
                  @NonNull String confirmEmail)
    {
        this(firstName, lastName, email, personType);
        this.confirmEmail = confirmEmail;
    }

    public boolean contactExistsInMailingList()
    {
        return Contact.contactExistsInMailingList(email);
    }

    public void addContactToMailingList()
    {
        Contact.addContactToMailingList(firstName, lastName, email);
    }

    public void removeContactFromMailingList()
    {
        Contact.removeContactFromMailingList(email);
    }
}