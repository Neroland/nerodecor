package za.co.neroland.nerodecor.chroma;

import java.util.Objects;

/** A value or a typed {@link ChromaError} with a short, data-free detail message. */
public final class ChromaResult<T> {

    private final T value;
    private final ChromaError error;
    private final String detail;

    private ChromaResult(T value, ChromaError error, String detail) {
        this.value = value;
        this.error = error;
        this.detail = detail;
    }

    public static <T> ChromaResult<T> ok(T value) {
        return new ChromaResult<>(Objects.requireNonNull(value), null, "");
    }

    public static <T> ChromaResult<T> fail(ChromaError error, String detail) {
        return new ChromaResult<>(null, Objects.requireNonNull(error), detail == null ? "" : detail);
    }

    public boolean isOk() {
        return error == null;
    }

    /** @throws IllegalStateException when this is a failure */
    public T value() {
        if (error != null) {
            throw new IllegalStateException("no value: " + error);
        }
        return value;
    }

    /** {@code null} on success. */
    public ChromaError error() {
        return error;
    }

    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return error == null ? "Ok[" + value + "]" : "Fail[" + error + " " + detail + "]";
    }
}
