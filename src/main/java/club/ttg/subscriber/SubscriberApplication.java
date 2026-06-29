package club.ttg.subscriber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса подписок и промо-кодов. {@code @EnableScheduling} включает
 * планировщик — им пользуется {@code SubscriptionExpiryScheduler} для фоновой обработки
 * истёкших подписок.
 */
@SpringBootApplication
@EnableScheduling
public class SubscriberApplication {

	public static void main(String[] args) {
		SpringApplication.run(SubscriberApplication.class, args);
	}

}
