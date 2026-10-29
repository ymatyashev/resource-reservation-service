package com.example.reservation.common.advice;

import com.example.reservation.common.exception.IdempotencyConflictException;
import com.example.reservation.common.exception.InvalidRequestException;
import com.example.reservation.common.exception.OverlappingReservationException;
import com.example.reservation.common.exception.ReservationNotFoundException;
import com.example.reservation.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handle(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Resource Not Found");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.5"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handle(ReservationNotFoundException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Reservation Not Found");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.5"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler(OverlappingReservationException.class)
    public ResponseEntity<ProblemDetail> handle(OverlappingReservationException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Overlapping Reservation");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.10"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ProblemDetail> handle(InvalidRequestException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Invalid Request");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.10"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handle(IdempotencyConflictException ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setTitle("Idempotency Conflict");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.10"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setTitle("Validation Error");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.10"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = "Malformed request body";
        // Extract a more specific message from the cause if available
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            String causeMsg = ex.getCause().getMessage();
            if (causeMsg.contains("UUID")) {
                message = "Invalid UUID format";
            } else if (causeMsg.contains("Instant") || causeMsg.contains("timestamp") || causeMsg.contains("date")) {
                message = "Invalid timestamp format";
            } else if (causeMsg.contains("Unexpected character") || causeMsg.contains("Expecting")) {
                message = "Invalid JSON format";
            }
        }
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setTitle("Bad Request");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.10"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setTitle("Bad Request");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.10"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpMediaTypeNotSupported(org.springframework.web.HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String message = "Unsupported media type: " + ex.getContentType();
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
        detail.setTitle("Unsupported Media Type");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.13"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Missing required parameter: " + ex.getParameterName();
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        detail.setTitle("Bad Request");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.5.10"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        detail.setTitle("Internal Server Error");
        detail.setType(URI.create("https://tools.ietf.org/html/rfc9110#section-15.6.1"));
        detail.setProperty("instance", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
    }
}
