package org.broadinstitute.lddp.security;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.handlers.util.Person;

public class AuthPayload
{

    private String firstName,lastName,email,recaptcha,surveyName,surveySessionId,auth0IdToken,participantId;

    private Person.PersonType personType;

    public AuthPayload(String firstName, String lastName, String email, String recaptcha) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.recaptcha = recaptcha;
    }

    public AuthPayload(String surveyName,String surveySessionId,String recaptcha) {
        this.surveyName = surveyName;
        this.surveySessionId = surveySessionId;
        this.recaptcha = recaptcha;
    }

    public AuthPayload(String auth0IdToken) {
        this.auth0IdToken = auth0IdToken;
    }

    public String getRecaptcha() {
        return recaptcha;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getSurveyName() {
        return surveyName;
    }

    public String getSurveySessionId() {
        return surveySessionId;
    }

    public String getAuth0IdToken() {
        return auth0IdToken;
    }

    public String getParticipantId() {
        return participantId;
    }

    public Person.PersonType getPersonType() {return personType;}

    public boolean isValidForSurveyBasedAuth() {
        return StringUtils.isNoneBlank(surveyName,surveySessionId);
    }
}
