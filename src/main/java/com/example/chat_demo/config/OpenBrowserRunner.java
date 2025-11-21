package com.example.chat_demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Desktop;
import java.net.URI;

/**
 * Tự động mở trình duyệt sau khi ứng dụng khởi động thành công.
 */
@Component
public class OpenBrowserRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(OpenBrowserRunner.class);

    @Value("${app.open-browser.enabled:false}")
    private boolean openBrowserEnabled;

    @Value("${app.open-browser.url:http://localhost:8081/swagger-ui.html}")
    private String openBrowserUrl;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!openBrowserEnabled) {
            log.debug("Open browser feature disabled.");
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(openBrowserUrl));
                    log.info("Opened browser at {}", openBrowserUrl);
                    return;
                }
            }
            log.warn("Desktop browse action is not supported on this environment.");
        } catch (Exception e) {
            log.warn("Failed to open browser at {}: {}", openBrowserUrl, e.getMessage());
        }
    }
}

