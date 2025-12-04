package com.example.chat_demo.api.exception;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler - Xử lý exception toàn cục và trả về error message rõ ràng
 */
@Slf4j
@RestControllerAdvice
@Hidden // Ẩn khỏi Swagger để tránh lỗi tương thích
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {
        log.warn("Validation error at {}: {}", request.getRequestURI(), e.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", System.currentTimeMillis());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("error", "Validation Failed");
        error.put("message", e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .findFirst()
            .orElse("Invalid request"));
        error.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException e,
            HttpServletRequest request) {
        log.warn("Constraint violation at {}: {}", request.getRequestURI(), e.getMessage());
        return buildErrorResponse(e, request, "Validation failed", HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException e,
            HttpServletRequest request) {
        log.warn("Illegal argument at {}: {}", request.getRequestURI(), e.getMessage());
        return buildErrorResponse(e, request, e.getMessage(), HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException e, 
            HttpServletRequest request) {
        log.error("RuntimeException at {}: ", request.getRequestURI(), e);
        return buildErrorResponse(e, request, "An error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception e, 
            HttpServletRequest request) {
        log.error("Exception at {}: ", request.getRequestURI(), e);
        return buildErrorResponse(e, request, "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * Build error response map
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            Exception e, 
            HttpServletRequest request, 
            String defaultMessage,
            HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", System.currentTimeMillis());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", e.getMessage() != null ? e.getMessage() : defaultMessage);
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(status).body(error);
    }
}

