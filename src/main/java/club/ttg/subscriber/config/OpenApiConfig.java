package club.ttg.subscriber.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Описание OpenAPI/Swagger сервиса: метаданные API и схема авторизации
 * {@code bearerAuth} (JWT в заголовке Authorization).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TTG Subscriber API",
                version = "v1",
                description = "API сервиса подписок и промо-кодов",
                contact = @Contact(
                        name = "TTG Club"
                ),
                license = @License(
                        name = "Proprietary"
                )
        ),
        servers = {
                @Server(
                        url = "/",
                        description = "Current server"
                )
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
