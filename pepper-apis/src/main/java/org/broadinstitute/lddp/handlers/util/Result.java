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
            Result result = (Result)o;
            if (this.code != result.code) {
                return false;
            } else {
                if (this.body != null) {
                    if (!this.body.equals(result.body)) {
                        return false;
                    }
                } else if (result.body != null) {
                    return false;
                }

                return true;
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

    public int getCode() {
        return this.code;
    }

    public InputStream getInputStream() {
        return this.inputStream;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
