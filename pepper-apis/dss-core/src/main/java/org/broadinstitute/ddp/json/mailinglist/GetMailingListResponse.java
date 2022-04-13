package org.broadinstitute.ddp.json.mailinglist;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.db.dao.JdbiMailingList.MailingListEntryDto;

@Value
@AllArgsConstructor
public class GetMailingListResponse {
    @SerializedName("firstName")
    String firstName;

    @SerializedName("lastName")
    String lastName;

    @SerializedName("email")
    String email;

    @SerializedName("info")
    String info;

    @SerializedName("dateCreated")
    long dateCreated;

    @SerializedName("isoLanguageCode")
    String languageCode;

    public GetMailingListResponse(final MailingListEntryDto dto) {
        this(dto.getFirstName(), dto.getLastName(), dto.getEmail(), dto.getInfo(),
                dto.getDateCreatedMillis() / 1000L, dto.getLanguageCode());
    }
}
