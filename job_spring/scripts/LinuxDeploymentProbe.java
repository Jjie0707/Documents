import java.sql.DriverManager;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class LinuxDeploymentProbe {
    public static void main(String[] args) {
        try {
            var env = new StandardEnvironment();
            ConfigDataEnvironmentPostProcessor.applyTo(env);
            require(Arrays.equals(env.getActiveProfiles(), new String[]{"linux"}));
            require("false".equals(env.getRequiredProperty("vision.model.enabled")));
            require("never".equals(env.getRequiredProperty("spring.sql.init.mode")));
            verifyMysql(env);
            verifyRedis(env);
        } catch (Exception error) {
            // Errors can contain connection details. Give the operator a safe stage result.
            System.err.println("连接探针未通过（" + error.getClass().getSimpleName() + "）。");
            System.exit(1);
        }
    }

    private static void verifyMysql(StandardEnvironment env) throws Exception {
        String url = env.getRequiredProperty("spring.datasource.url");
        require(url.startsWith("jdbc:mysql://127.0.0.1:3306/job_tracker?"));
        require(url.contains("&sslMode=REQUIRED"));
        require("job_tracker_linux".equals(env.getRequiredProperty("spring.datasource.username")));
        try (var connection = DriverManager.getConnection(url, "job_tracker_linux",
                env.getRequiredProperty("spring.datasource.password"));
             var statement = connection.createStatement()) {
            statement.execute("START TRANSACTION READ ONLY");
            try (var result = statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), @@session.time_zone")) {
                require(result.next()
                        && "job_tracker".equals(result.getString(1))
                        && "job_tracker_linux@127.0.0.1".equals(result.getString(2))
                        && "+08:00".equals(result.getString(3)));
            }
            try (var result = statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                require(result.next() && !result.getString(2).isBlank());
                System.out.println("JDBC OK: job_tracker / job_tracker_linux@127.0.0.1 / +08:00 / TLS " + result.getString(2));
            }
            statement.execute("ROLLBACK");
        }
    }

    private static void verifyRedis(StandardEnvironment env) throws Exception {
        require("127.0.0.1".equals(env.getRequiredProperty("spring.data.redis.host")));
        require("6379".equals(env.getRequiredProperty("spring.data.redis.port")));
        require("0".equals(env.getRequiredProperty("spring.data.redis.database")));
        require("job_tracker_app".equals(env.getRequiredProperty("spring.data.redis.username")));
        var config = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        config.setUsername("job_tracker_app");
        config.setPassword(RedisPassword.of(env.getRequiredProperty("spring.data.redis.password")));
        var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build();
        var factory = new LettuceConnectionFactory(config, client);
        factory.afterPropertiesSet();
        try (var connection = factory.getConnection()) {
            require("PONG".equals(connection.ping()));
            System.out.println("Redis OK: 127.0.0.1:6379 / DB 0 / job_tracker_app / PONG");
        } finally {
            factory.destroy();
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Linux deployment expectation did not match");
    }
}
