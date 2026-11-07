package com.savanna.backend.error;

import java.util.List;

public record ErrorResponse(String code, String message, List<FieldError> errors) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public record FieldError(String field, String message) {
    }
}

