package org.broadinstitute.ddp.handlers.util;

import java.io.InputStream;

public class Result
{
    private int code;
    private String body = ""; //null body makes spark unhappy
    private InputStream inputStream;

    public Result(int code, String body)
    {
        this.code = code;
        this.body = body;
    }

    public Result(int code, InputStream inputStream)
    {
        this.code = code;
        this.inputStream = inputStream;
    }

    public Result(int code) {
        this.code = code;
        this.body = "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Result result = (Result) o;

        if (code != result.code) return false;
        if (body != null ? !body.equals(result.body) : result.body != null) return false;

        return true;
    }

    @Override
    public String toString() {
        return "Result(code=" + code + ", body=" + body + ")";
    }

    public String getBody() {
        return body;
    }

    public int getCode() {
        return code;
    }

    public InputStream getInputStream() { return inputStream;}

    public void setBody(String body) {
        this.body = body;
    }
}
