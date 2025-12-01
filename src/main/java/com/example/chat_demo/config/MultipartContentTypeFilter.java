package com.example.chat_demo.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Filter để normalize Content-Type header cho multipart/form-data
 * 
 * Browser tự động thêm charset=UTF-8 vào Content-Type:
 *   Content-Type: multipart/form-data; boundary=...; charset=UTF-8
 * 
 * Nhưng Spring Boot có thể không chấp nhận charset trong multipart.
 * Filter này sẽ loại bỏ charset để đảm bảo compatibility.
 */
@Slf4j
@Component
@Order(1) // Chạy sớm, trước các filter khác
public class MultipartContentTypeFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest) {
            String contentType = httpRequest.getContentType();
            log.info("MultipartContentTypeFilter contentType={}", contentType);
            
            // Chỉ xử lý multipart/form-data có charset
            if (contentType != null && 
                contentType.startsWith("multipart/form-data") &&
                contentType.contains("charset")) {
                
                log.debug("Normalizing multipart Content-Type: {} -> removing charset", contentType);
                
                // Loại bỏ charset từ Content-Type
                String normalizedContentType = contentType.replaceAll(";\\s*charset=[^;]+", "");
                
                // Wrap request với Content-Type đã normalize
                HttpServletRequest wrappedRequest = new ContentTypeRequestWrapper(httpRequest, normalizedContentType);
                chain.doFilter(wrappedRequest, response);
                return;
            }
        }
        
        // Không phải multipart hoặc không có charset, pass through
        chain.doFilter(request, response);
    }

    /**
     * Request wrapper để override Content-Type header
     */
    private static class ContentTypeRequestWrapper extends HttpServletRequestWrapper {
        private final String contentType;

        public ContentTypeRequestWrapper(HttpServletRequest request, String contentType) {
            super(request);
            this.contentType = contentType;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getHeader(String name) {
            if ("content-type".equalsIgnoreCase(name)) {
                return contentType;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Enumeration<String> originalHeaders = super.getHeaderNames();
            java.util.List<String> headerNames = Collections.list(originalHeaders);
            
            // Đảm bảo content-type có trong danh sách
            if (!headerNames.contains("content-type")) {
                headerNames.add("content-type");
            }
            
            return Collections.enumeration(headerNames);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("content-type".equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(contentType));
            }
            return super.getHeaders(name);
        }
    }
}

