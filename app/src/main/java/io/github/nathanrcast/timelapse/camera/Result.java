package io.github.nathanrcast.timelapse.camera;

import java.lang.reflect.InvocationTargetException;

// What a SafeCamera call returns: a value, or a typed failure naming the call. UNSUPPORTED means the probe says this
// firmware lacks the method, so it was never called; FAILED means the call (or the firmware behind it) threw.
public final class Result<T> {
    public enum Status { OK, UNSUPPORTED, FAILED }

    private static final int MAX_MESSAGE = 160;

    private final Status status;
    private final T value;
    private final String call;
    private final String error;

    private Result(Status status, T value, String call, String error) {
        this.status = status;
        this.value = value;
        this.call = call;
        this.error = error;
    }

    public static <T> Result<T> ok(T value) {
        return new Result<T>(Status.OK, value, null, null);
    }

    public static <T> Result<T> unsupported(String call) {
        return new Result<T>(Status.UNSUPPORTED, null, call, null);
    }

    public static <T> Result<T> failed(String call, Throwable t) {
        return new Result<T>(Status.FAILED, null, call, describe(t));
    }

    public static <T> Result<T> failed(String call, String error) {
        return new Result<T>(Status.FAILED, null, call, error);
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public Status status() {
        return status;
    }

    public T get() {
        if (status != Status.OK)
            throw new IllegalStateException(toString());
        return value;
    }

    public T or(T fallback) {
        return status == Status.OK ? value : fallback;
    }

    public String call() {
        return call;
    }

    public String error() {
        return error;
    }

    @Override
    public String toString() {
        if (status == Status.OK)
            return "OK";
        return status + " " + call + (error == null ? "" : ": " + error);
    }

    static String describe(Throwable t) {
        while (t instanceof InvocationTargetException && t.getCause() != null)
            t = t.getCause();
        String msg = t.getMessage();
        if (msg == null)
            return t.getClass().getName();
        if (msg.length() > MAX_MESSAGE)
            msg = msg.substring(0, MAX_MESSAGE) + "...";
        return t.getClass().getName() + ": " + msg;
    }
}
