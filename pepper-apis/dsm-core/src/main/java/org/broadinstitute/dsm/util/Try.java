package org.broadinstitute.dsm.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Try class is an abstraction for try/catch procedures.
 * Try class suggests a few methods for handling some common situations
 * associated with try/catch procedures.
 * These situations can be:
 *  - folding the computation with success function or default value
 *  - finalizing computation with Runnables
 *  - catching user-specified exception and returning default value
 *  - catching user-specified exception and running some task afterwards
 */
public abstract class Try<T> {

    /**
    * Every Try computation starts with calling Try.evaluate(() -> [some Callable here]).
    * Returns either Success or Failure instance based on computation
    * @param callable a lazy computation which is not evaluated on the call site, instead it's evaluated here.
    */
    public static <T> Try<T> evaluate(Callable<T> callable) {
        try {
            return new Success<>(callable.call());
        } catch (Exception e) {
            return (Try<T>) new Failure(e);
        }
    }

    public static <T> Try<T> evaluate(Runnable runnable) {
        try {
            runnable.run();
            return new Success<>(null);
        } catch (Exception e) {
            return (Try<T>) new Failure(e);
        }
    }

    /**
     * Returns the value based on if the main computation succeeded or failed
     * @param successMapper a function which can transform the successful value, or leave it as it is
     * @param defaultValue  a default value which will be returned if the computation fails
     */
    public <V> V fold(Function<? super T, ? extends V> successMapper, V defaultValue) {
        if (this instanceof Success) {
            Success<T> success = (Success<T>) this;
            return successMapper.apply(success.getValue());
        } else {
            return defaultValue;
        }
    }

    /**
     * Runs the tasks based on if the main computation succeeded or failed
     * @param onSuccess a runnable which will be executed in case of successful computation
     * @param onFailure a runnable which will be executed in case of failed computation
     */
    public void finalizeWith(Runnable onSuccess, Runnable onFailure) {
        if (this instanceof Success) {
            onSuccess.run();
        } else {
            onFailure.run();
        }
    }

    /**
     * Returns the default value if the evaluation procedure will catch the user-specified exception
     * otherwise returns the successive value
     * @param exception     a user specified exception which can be caught
     * @param defaultMapper a function for transforming error into default value
     */
    public <V> V ifThrowsThenGet(Class<? extends Exception> exception, Function<? super Exception, V> defaultMapper) {
        if (this instanceof Failure) {
            Failure failure = (Failure) this;
            if (failure.getValue().getClass().equals(exception)) {
                return defaultMapper.apply(failure.getValue());
            } else {
                throw new UncaughtException(failure.getValue());
            }
        } else {
            return (V) getValue();
        }
    }

    /**
     * Runs the user specified task if the evaluation procedure will catch the user-specified exception
     * otherwise it won't run any tasks
     * @param exception a user specified exception which can be caught
     * @param consumer  an exception consumer for running the task
     */
    public void ifThrowsCatchAndThenRun(Class<? extends Exception> exception, Consumer<? super Exception> consumer) {
        if (this instanceof Failure) {
            Failure failure = (Failure) this;
            if (failure.getValue().getClass().equals(exception)) {
                consumer.accept(failure.getValue());
            }
        }
    }

    /**
     * abstract method which must be implemented by the children of Try
     */
    abstract T getValue();

    /**
     * A success case which encapsulates the successful computation
     */
    private static final class Success<T> extends Try<T> {

        T value;

        public Success(T value) {
            this.value = value;
        }

        @Override
        T getValue() {
            return value;
        }
    }

    /**
     * A failed case which ensapsulates the failed computation
     */
    private static final class Failure extends Try<Exception> {

        Exception exception;

        public Failure(Exception exception) {
            this.exception = exception;
        }

        @Override
        Exception getValue() {
            return exception;
        }
    }

    /**
     * A special exception for indicating that user could not catch it
     */
    private static final class UncaughtException extends RuntimeException {

        private final Exception exception;

        public UncaughtException(Exception exception) {
            this.exception = exception;
        }

        @Override
        public String getMessage() {
            return exception.getMessage();
        }

        @Override
        public String getLocalizedMessage() {
            return exception.getLocalizedMessage();
        }

        @Override
        public synchronized Throwable getCause() {
            return exception.getCause();
        }

        @Override
        public synchronized Throwable initCause(Throwable cause) {
            return exception.initCause(cause);
        }

        @Override
        public String toString() {
            return exception.toString();
        }

        @Override
        public void printStackTrace() {
            exception.printStackTrace();
        }

        @Override
        public void printStackTrace(PrintStream s) {
            exception.printStackTrace(s);
        }

        @Override
        public void printStackTrace(PrintWriter s) {
            exception.printStackTrace(s);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return exception.fillInStackTrace();
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return exception.getStackTrace();
        }

        @Override
        public void setStackTrace(StackTraceElement[] stackTrace) {
            exception.setStackTrace(stackTrace);
        }
    }

}
