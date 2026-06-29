package club.ttg.subscriber.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Включает JPA-аудит, чтобы поля {@code @CreatedDate}/{@code @LastModifiedDate}
 * сущностей (createdAt/updatedAt) проставлялись автоматически.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
