package org.broadinstitute.ddp.model.migration;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class MailingListData {
    @SerializedName("mailing_list_data")
    private List<MailingListDatum> mailingListData = null;
}
