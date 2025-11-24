package com.example.chat_demo.api.exception;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException e, 
            HttpServletRequest request) {
        log.error("RuntimeException at {}: ", request.getRequestURI(), e);
        return buildErrorResponse(e, request, "An error occurred");
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception e, 
            HttpServletRequest request) {
        log.error("Exception at {}: ", request.getRequestURI(), e);
        return buildErrorResponse(e, request, "An unexpected error occurred");
    }
    
    /**
     * Build error response map
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            Exception e, 
            HttpServletRequest request, 
            String defaultMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", System.currentTimeMillis());
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("error", "Internal Server Error");
        error.put("message", e.getMessage() != null ? e.getMessage() : defaultMessage);
        error.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

