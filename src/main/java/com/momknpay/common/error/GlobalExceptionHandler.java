package com.momknpay.common.error;

import com.momknpay.common.ratelimit.RateLimitedException;
import com.momknpay.common.web.Headers;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.PropertyBindingException;

/**
 * The single place that turns exceptions into the error envelope (SRS FR-COM-3). Nothing else
 * shapes error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        log.debug("api.error code={} field={}", ex.getCode(), ex.getField());
        return respond(ex.getCode(), ex.getField());
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ErrorResponse> handleRateLimited(RateLimitedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(Headers.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.of(ErrorCode.RATE_LIMITED, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        return validation(fieldError == null ? null : fieldError.getField());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex) {
        String field =
                ex.getParameterValidationResults().stream()
                        .findFirst()
                        .map(result -> inputName(result.getMethodParameter()))
                        .orElse(null);
        return validation(field);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String field =
                ex.getConstraintViolations().stream()
                        .findFirst()
                        .map(violation -> lastNode(violation.getPropertyPath().toString()))
                        .orElse(null);
        return validation(field);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return validation(ex.getHeaderName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        return validation(ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validation(inputName(ex.getParameter()));
    }

    /** Malformed JSON, wrong types, and unknown properties (e.g. a read-only {@code mobile}). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return validation(jsonField(ex.getCause()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return validation("Content-Type");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, null);
    }

    /** Fallback: log with the stack trace, reveal nothing to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("unhandled.exception type={}", ex.getClass().getName(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, null);
    }

    static ResponseEntity<ErrorResponse> respond(ErrorCode code, String field) {
        return ResponseEntity.status(HttpStatus.valueOf(code.status()))
                .body(ErrorResponse.of(code, field));
    }

    private static ResponseEntity<ErrorResponse> validation(String field) {
        return respond(ErrorCode.VALIDATION_ERROR, field);
    }

    /** Header, query or path name as the client sees it, not the Java parameter name. */
    private static String inputName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isEmpty()) {
            return header.name();
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.name().isEmpty()) {
            return param.name();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isEmpty()) {
            return path.name();
        }
        return parameter.getParameterName();
    }

    private static String jsonField(Throwable cause) {
        if (cause instanceof PropertyBindingException unknown) {
            return unknown.getPropertyName();
        }
        if (cause instanceof JacksonException jackson) {
            List<JacksonException.Reference> path = jackson.getPath();
            if (!path.isEmpty()) {
                return path.getLast().getPropertyName();
            }
        }
        return null;
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }
}
