package io.velo.was.servlet;

public record ErrorPageSpec(
        Integer statusCode,
        Class<? extends Throwable> exceptionType,
        String location
) {
    public ErrorPageSpec {
        if (statusCode == null && exceptionType == null) {
            throw new IllegalArgumentException("Either statusCode or exceptionType must be provided");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("location is required");
        }
    }

    public boolean matchesStatus(int statusCode) {
        return this.statusCode != null && this.statusCode == statusCode;
    }

    public boolean matchesException(Throwable throwable) {
        return throwable != null && exceptionType != null && exceptionType.isAssignableFrom(throwable.getClass());
    }
}
