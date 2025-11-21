package com.example.chat_demo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.servers.Servers;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Omnichannel Chat Demo API",
                version = "1.0",
                description = """
                        API cho backend Omnichannel Chat Demo (Telegram focus). \
                        Dùng để test Webhook, Staff Dashboard và các helper endpoints.
                        """,
                contact = @Contact(name = "Omnichannel Team", email = "support@example.com"),
                license = @License(name = "MIT")
        ),
        servers = {
                @Server(url = "http://localhost:8081", description = "Local")
        }
)
public class OpenApiConfig {
}

