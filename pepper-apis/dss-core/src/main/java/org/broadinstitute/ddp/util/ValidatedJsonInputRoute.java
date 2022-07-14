package org.broadinstitute.ddp.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.validation.ValidationException;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.json.errors.ApiError;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Base class to be used to implement Spark Routes that handle incoming JSON payloads
 * that can be converted to POJOs using Google GSON. Optionally (and preferably) the target POJO class
 * has been annotated with {@link javax.validation.constraints} annotations, in which case the validations will be executed.
 * Few methods can be reimplemented for special purposes, see Javadoc comments.
 *
 * @param <T> The target class for the body-to-POJO conversion
 */
@Slf4j
public abstract class ValidatedJsonInputRoute<T> implements Route {
    private Gson gson;
    private GsonPojoValidator validator;

    /**
     * The handle method for the Spark route, but extended to include the POJO'd body.
     * Will only be executed if the JSON to POJO and the validations passed.
     * Otherwise the client would have been sent a response with the appropriate error code and error message
     */
    public abstract Object handle(Request request, Response response, T dataObject) throws Exception;

    @Override
    public Object handle(Request request, Response response) throws Exception {
        T deserializedBodyObject = handleJsonUnmarshall(request, response);
        if (deserializedBodyObject == null) {
            return null;
        }
        if (handleJsonValidation(deserializedBodyObject, request, response)) {
            return null;
        }
        return handle(request, response, deserializedBodyObject);
    }

    /**
     * Override this if class not being properly inferred.
     *
     * @param request the request
     * @return the target class for the request body to POJO object
     */
    @SuppressWarnings("unchecked")
    protected Class<T> getTargetClass(Request request) {
        Class clazz = getClass();
        Type[] typeArguments = ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments();
        return (Class<T>) typeArguments[0];
    }

    /**
     * Getter with lazy-initialization
     *
     * @return a Gson instance
     */
    protected Gson getGson() {
        if (gson == null) {
            gson = buildGson();
        }
        return gson;
    }

    /**
     * Getter for validator with lazy-initialization.
     *
     * @return a validator instance
     */
    protected GsonPojoValidator getValidator() {
        if (validator == null) {
            validator = new GsonPojoValidator();
        }
        return validator;
    }

    /**
     * Override if you want to parse Json your way.
     *
     * @return the target class for the request body to POJO object
     */
    protected T unmarshallJson(Request request) throws JsonSyntaxException, JsonParseException {
        return getGson().fromJson(request.body(), getTargetClass(request));
    }

    /**
     * Override if you want a customized Gson.
     */
    protected Gson buildGson() {
        return GsonUtil.standardGson();
    }

    /**
     * Override to set status code when json unmarshalling fails. Defaults to 400.
     */
    protected int getUnmarshallErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    /**
     * Override to set status code when json constraint validation fails. Defaults to 422.
     */
    protected  int getValidationErrorStatus() {
        return HttpStatus.SC_UNPROCESSABLE_ENTITY;
    }

    private boolean handleJsonValidation(T deserializedBodyObject, Request request, Response response) {
        try {
            List<JsonValidationError> validationErrors = validateObject(deserializedBodyObject, request);
            if (!validationErrors.isEmpty()) {
                ApiError err = new ApiError(ErrorCodes.BAD_PAYLOAD, buildErrorMessage(validationErrors));
                ResponseUtil.haltError(response, getValidationErrorStatus(), err);
                return true;
            }
        } catch (ValidationException e) {
            log.error("There was an error trying to validate request payload", e);
            ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, "Error validating request payload");
            ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
            return true;
        }
        return false;
    }

    private T handleJsonUnmarshall(Request request, Response response) {
        T deserializedBodyObject;
        try {
            deserializedBodyObject = unmarshallJson(request);
        } catch (JsonSyntaxException e) {
            log.warn("JSON payload could not be converted to object of class: " + getTargetClass(request).getName(), e);
            ApiError err = new ApiError(ErrorCodes.BAD_PAYLOAD, "Request payload could not be parsed and converted to expected type");
            throw ResponseUtil.haltError(response, getUnmarshallErrorStatus(), err);
        } catch (JsonParseException exception) {
            var cause = Optional.ofNullable(exception.getCause()).orElse(exception);

            /*
             * See if there's a better way of handling these errors. The JsonParseException _works_
             * but Google's documentation seems to indicate these are intended more for errors in the
             * structure of the JSON, and not errors in the content.
             * 
             * 2022.07.13 - bskinner
             */
            if (cause instanceof DateTimeParseException) {
                // Likely caused by an adapter for a LocalDate or a LocalDateTime
                // failing due to a poorly formatted date string
                log.warn("failed to unmarshall json object: a date-typed property value does not match the required format", cause);
                var responseError = new ApiError(ErrorCodes.BAD_PAYLOAD, exception.getMessage());
                throw ResponseUtil.haltError(response, getValidationErrorStatus(), responseError);
            } else {
                var message = String.format("failed to parse json: %s", cause.getMessage());
                log.warn("{}", message, cause);
                var responseError = new ApiError(ErrorCodes.BAD_PAYLOAD, message);
                throw ResponseUtil.haltError(response, getUnmarshallErrorStatus(), responseError);
            }
        }

        if (deserializedBodyObject == null) {
            ApiError err = new ApiError(ErrorCodes.BAD_PAYLOAD, "Expected request payload but none was found");
            ResponseUtil.haltError(response, getUnmarshallErrorStatus(), err);
            return null;
        }
        return deserializedBodyObject;
    }

    /**
     * Where we actually do the validation. Default implementation uses the Hibernate Validator.
     */
    protected List<JsonValidationError> validateObject(T deserializedObject, Request request) {
        return getValidator().validateAsJson(deserializedObject);
    }

    protected String buildErrorMessage(List<JsonValidationError> validationErrors) {
        StringBuilder builder = new StringBuilder();
        Iterator<JsonValidationError> errorIter = validationErrors.iterator();
        while (errorIter.hasNext()) {
            JsonValidationError current = errorIter.next();
            if (CollectionUtils.isNotEmpty(current.getPropertyPath())) {
                builder.append("Property at '");
                builder.append(current.getPropertyPathAsString());
                builder.append("' ");
                builder.append(current.getMessage());
            } else {
                builder.append("Found an error in payload");
            }
            if (errorIter.hasNext()) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }
}
