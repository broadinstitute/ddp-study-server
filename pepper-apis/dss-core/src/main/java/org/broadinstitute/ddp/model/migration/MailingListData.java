package org.broadinstitute.ddp.model.migration;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class MailingListData {

    @SerializedName("mailing_list_data")
    private List<MailingListDatum> mailingListData = null;

    public List<MailingListDatum> getMailingListData() {
        return mailingListData;
    }

    public void setMailingListData(List<MailingListDatum> mailingListData) {
        this.mailingListData = mailingListData;
    }

}
