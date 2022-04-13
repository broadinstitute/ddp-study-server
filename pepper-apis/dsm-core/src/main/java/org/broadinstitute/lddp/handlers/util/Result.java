package org.broadinstitute.lddp.handlers.util;

import java.io.InputStream;

public class Result {
    private int code;
    private String body = "";
    private InputStream inputStream;

    public Result(int code, String body) {
        this.code = code;
        this.body = body;
    }

    public Result(int code, InputStream inputStream) {
        this.code = code;
        this.inputStream = inputStream;
    }

    public Result(int code) {
        this.code = code;
        this.body = "";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            Result result = (Result) o;
            if (this.code != result.code) {
                return false;
            } else {
                if (this.body != null) {
                    return this.body.equals(result.body);
                } else {
                    return result.body == null;
                }

            }
        } else {
            return false;
        }
    }

    public String toString() {
        return "Result(code=" + this.code + ", body=" + this.body + ")";
    }

    public String getBody() {
        return this.body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getCode() {
        return this.code;
    }

    public InputStream getInputStream() {
        return this.inputStream;
    }
}
