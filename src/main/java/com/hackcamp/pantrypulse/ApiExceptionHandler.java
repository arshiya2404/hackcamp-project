package com.hackcamp.pantrypulse;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(
            ResponseStatusException error) {

        String message = error.getReason() == null
                ? "The request could not be completed."
                : error.getReason();

        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(
            MaxUploadSizeExceededException error) {

        return reply(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Use an image smaller than 5 MB.");
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<Map<String, String>> invalid(
            Exception error) {

        return reply(
                HttpStatus.BAD_REQUEST,
                "The request is incomplete or invalid. "
                        + "Check the selected file or entered values.");
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> connection(
            ResourceAccessException error) {

        return reply(
                HttpStatus.GATEWAY_TIMEOUT,
                "The AI service could not respond in time. Please try again.");
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> upstream(
            RestClientResponseException error) {

        log.warn(
                "AI request failed with HTTP {}",
                error.getStatusCode().value());

        return reply(
                HttpStatus.BAD_GATEWAY,
                "The AI request failed. Check the server's API key, "
                        + "model access, and quota.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(
            Exception error) {

        // Do not log report contents or upstream response bodies.
        log.warn(
                "Request failed: {}",
                error.getClass().getSimpleName());

        return reply(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The request could not be completed. Please retry; "
                        + "if it persists, check the server configuration.");
    }

    private ResponseEntity<Map<String, String>> reply(
            HttpStatus status,
            String message) {

        return ResponseEntity.status(status)
                .body(Map.of("message", message));
    }
}
