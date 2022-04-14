package org.broadinstitute.ddp.cf.json;

import com.google.gson.annotations.SerializedName;

public class Attachment {

    @SerializedName("name")
    private final String name;

    @SerializedName("size")
    private final long size;

    public Attachment(String name, long size) {
        this.name = name;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }
}
