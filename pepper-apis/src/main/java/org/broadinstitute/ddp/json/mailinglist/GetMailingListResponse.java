package org.broadinstitute.ddp.json.mailinglist;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.db.dao.JdbiMailingList.MailingListEntryDto;

public class GetMailingListResponse {

    @SerializedName("firstName")
    private String firstName;
    @SerializedName("lastName")
    private String lastName;
    @SerializedName("email")
    private String email;
    @SerializedName("info")
    private String info;
    @SerializedName("dateCreated")
    private long dateCreated;

    public GetMailingListResponse(MailingListEntryDto dto) {
        this.firstName = dto.getFirstName();
        this.lastName = dto.getLastName();
        this.email = dto.getEmail();
        this.info = dto.getInfo();
        this.dateCreated = dto.getDateCreatedMillis() / 1000L;
    }

    public String getEmail() {
        return email;
    }

    public long getDateCreated() {
        return dateCreated;
    }

}
