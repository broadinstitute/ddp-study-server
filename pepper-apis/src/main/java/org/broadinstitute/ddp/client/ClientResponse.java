package org.broadinstitute.ddp.client;

/**
 * A general container for a HTTP call response. Useful for clients to return both a status code and response body.
 *
 * @param <T> type of body response
 */
public class ClientResponse<T> {

    private final int statusCode;
    private final T body;

    public ClientResponse(int statusCode, T body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public T getBody() {
        return body;
    }
}
