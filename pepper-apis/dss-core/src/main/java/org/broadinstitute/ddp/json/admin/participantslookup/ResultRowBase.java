package org.broadinstitute.ddp.json.admin.participantslookup;

import com.google.gson.annotations.SerializedName;

/**
 * Base set of data to be fetched during participants lookup.
 */
public class ResultRowBase {

    @SerializedName("guid")
    protected String guid;

    @SerializedName("hruid")
    protected String hruid;

    @SerializedName("firstName")
    protected String firstName;

    @SerializedName("lastName")
    protected String lastName;

    @SerializedName("email")
    protected String email;


    public ResultRowBase() {}

    public ResultRowBase(ResultRowBase resultRowBase) {
        this.guid = resultRowBase.getGuid();
        this.hruid = resultRowBase.getHruid();
        this.firstName = resultRowBase.getFirstName();
        this.lastName = resultRowBase.getLastName();
        this.email = resultRowBase.getEmail();
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getHruid() {
        return hruid;
    }

    public void setHruid(String hruid) {
        this.hruid = hruid;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
