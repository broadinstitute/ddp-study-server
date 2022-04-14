package org.broadinstitute.ddp.util;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;

public class TestName {
    @SerializedName("firstName")
    @Pattern(regexp = "[a-zA-Z]+")
    private String first;
    @SerializedName("lastName")
    @Pattern(regexp = "[a-zA-Z]+")
    @NotEmpty
    private String last;

    @Pattern(regexp = "[a-zA-Z]+")

    public TestName(String first, String last) {
        this.first = first;
        this.last = last;
    }

    public String getFirst() {
        return first;
    }

    public void setFirst(String first) {
        this.first = first;
    }

    public String getLast() {
        return last;
    }

    public void setLast(String last) {
        this.last = last;
    }

    public String getFullName() {
        return StringUtils.defaultString(this.first, "?") + StringUtils.defaultString(this.last, "?");
    }
}
