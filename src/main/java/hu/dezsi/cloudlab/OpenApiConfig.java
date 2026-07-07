package hu.dezsi.cloudlab;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Java Cloud Platform Lab API")
                        .description("API documentation for the Java Cloud Platform Lab task service.")
                        .version("0.0.1"));
    }
}
