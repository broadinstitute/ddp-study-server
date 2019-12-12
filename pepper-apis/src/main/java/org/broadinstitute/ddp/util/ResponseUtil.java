package org.broadinstitute.ddp.util;

import static spark.Spark.halt;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.json.Error;
import org.broadinstitute.ddp.transformers.SimpleJsonTransformer;
import spark.HaltException;
import spark.Response;

public class ResponseUtil {

    private static final int GENERIC_INPUT_ERROR = 422;
    private static final SimpleJsonTransformer transformer = new SimpleJsonTransformer();

    /**
     * Immediately sets the status of the underlying
     * response to {@link #GENERIC_INPUT_ERROR} and
     * sets json body to {@param errorCode}.
     */
    public static void halt422ErrorResponse(Response res, String errorCode) {
        String errorJson = new Gson().toJson(new Error(errorCode));
        res.raw().setContentType(ContentType.APPLICATION_JSON.getMimeType());
        halt(GENERIC_INPUT_ERROR, errorJson);

    }

    /**
     * Immediately sets the status of the underlying response to 400 and sets json body to {@param errorCode}.
     */
    public static void halt400ErrorResponse(Response res, String errorCode) {
        String errorJson = new Gson().toJson(new Error(errorCode));
        res.raw().setContentType(ContentType.APPLICATION_JSON.getMimeType());
        halt(HttpStatus.SC_BAD_REQUEST, errorJson);
    }

    /**
     * Helper to halt the request handler and set an error response. Callers should correspondingly throw when invoking this.
     *
     * @param res    the response
     * @param status the http status code to set
     * @param error  the response to return as the body
     * @throws HaltException unconditionally thrown to halt handler
     */
    public static HaltException haltError(Response res, int status, Object error) {
        res.raw().setContentType(ContentType.APPLICATION_JSON.getMimeType());
        throw halt(status, transformer.render(error));
    }
}
