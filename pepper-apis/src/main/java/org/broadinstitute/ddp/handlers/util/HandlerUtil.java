package org.broadinstitute.ddp.handlers.util;

import com.google.common.net.MediaType;
import lombok.NonNull;
import spark.Request;
import spark.Response;

public class HandlerUtil {

    public static MediaType responseType = MediaType.PLAIN_TEXT_UTF_8;

    public static final String PATHPARAM_SURVEY = ":survey";
    public static final String PATHPARAM_SESSIONID = ":sessionid";

    public static void setResponseType(@NonNull Request request, @NonNull Response response)
    {
        //set default
        response.type("text/plain");

        String accept = request.headers("Accept");
        if (accept == null) return;

        if (accept.contains("application/json"))
        {
            response.type("application/json");
        }
        else if (accept.contains("text/html"))
        {
            response.type("text/html");
        }
    }
}
