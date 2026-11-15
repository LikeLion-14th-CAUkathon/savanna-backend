package com.savanna.backend.dto.response;

/** Typed form of the existing status/message/data envelope so Swagger can describe nested DTOs. */
public record DataResponse<T>(int status,String message,T data) {}
