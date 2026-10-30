package jjie.job_spring.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VisionQuotaProperties.class)
public class VisionQuotaConfig {
    @Bean
    Clock visionQuotaClock() {
        return Clock.systemUTC();
    }
}
