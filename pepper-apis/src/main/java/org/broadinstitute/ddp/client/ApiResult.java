package org.broadinstitute.ddp.client;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A general container for wrapping a response from an API call.
 *
 * <p>An API call to a service can fail in several ways. Status code could be something that's not 2xx. There might be
 * a response payload with error information. Or an exception is thrown due to network issues. To allow flexibility to
 * callers, we wrap the different outcomes in a container like this one.
 *
 * <p>When request is successful, this result should include a body response. When request is unsuccessful (e.g. 400 or
 * 422) and API returned error information, this result should include an error response. If there was an exception
 * caught, then it can optionally be attached to result so callers can decide what to do with it.
 *
 * @param <B> type of body response
 * @param <E> type of error response
 */
public class ApiResult<B, E> {

    private final int statusCode;
    private final B body;
    private final E error;
    private Exception thrown;

    // Convenience helper to create successful result.
    public static <B, E> ApiResult<B, E> ok(int statusCode, B body) {
        return new ApiResult<>(statusCode, body, null);
    }

    // Convenience helper to create unsuccessful result.
    public static <B, E> ApiResult<B, E> err(int statusCode, E error) {
        return new ApiResult<>(statusCode, null, error);
    }

    // Convenience helper to create result with a thrown exception.
    public static <B, E> ApiResult<B, E> thrown(Exception thrown) {
        var res = new ApiResult<B, E>(500, null, null);
        res.thrown = thrown;
        return res;
    }

    public ApiResult(int statusCode, B body, E error) {
        this.statusCode = statusCode;
        this.body = body;
        this.error = error;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public B getBody() {
        return body;
    }

    public E getError() {
        return error;
    }

    public Exception getThrown() {
        return thrown;
    }

    public boolean hasBody() {
        return body != null;
    }

    public boolean hasError() {
        return error != null;
    }

    public boolean hasThrown() {
        return thrown != null;
    }

    /**
     * Create from this result a new result with given exception attached.
     *
     * @param thrown the thrown exception
     * @return a new result, with the exception attached
     */
    public ApiResult<B, E> attachThrown(Exception thrown) {
        var res = new ApiResult<>(statusCode, body, error);
        res.thrown = thrown;
        return res;
    }

    /**
     * Runs the given callback if this result has an exception attached.
     *
     * @param callback the callback
     * @return this result, for chaining
     */
    public ApiResult<B, E> runIfThrown(Consumer<Exception> callback) {
        if (thrown != null) {
            callback.accept(thrown);
        }
        return this;
    }

    /**
     * Propagate the exception if this result has an exception attached.
     *
     * @param callback takes the attached exception and supplies an exception to throw
     * @return this result, for chaining if not thrown
     * @throws X the exception to rethrow
     */
    public <X extends Throwable> ApiResult<B, E> rethrowIfThrown(Function<Exception, X> callback) throws X {
        if (thrown != null) {
            throw callback.apply(thrown);
        }
        return this;
    }
}
