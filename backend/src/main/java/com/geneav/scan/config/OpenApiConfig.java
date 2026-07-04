package com.geneav.scan.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI geneavOpenApi() {
        return new OpenAPI().info(new Info()
                .title("geneav Scan API")
                .version("v1")
                .description("Scan documents for malware. Backed by ClamAV.")
                .contact(new Contact().name("geneav").email("support@geneav.example"))
                .license(new License().name("MIT")));
    }
}
