package club.ttg.subscriber.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Настройка общего Jackson {@link ObjectMapper}: лояльный разбор (не падать на
 * неизвестных полях), читаемый вывод и поддержка java.time через {@link JavaTimeModule}.
 */
@Configuration
public class ObjectMapperConfig {

    /**
     * Общий {@link ObjectMapper}: игнорирует неизвестные поля при десериализации,
     * форматирует вывод и сериализует типы java.time (Instant и пр.).
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
