package org.broadinstitute.ddp.model.suggestion;

import com.google.gson.annotations.SerializedName;

public class PatternMatch {
    @SerializedName("offset")
    private int offset;
    @SerializedName("length")
    private int length;

    public PatternMatch(int offset, int length) {
        this.offset = offset;
        this.length = length;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }
}
