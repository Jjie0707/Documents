package jjie.job_spring.health.controller;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {
        // 只验证连接，不查询用户记录，也不修改数据库。
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(new HealthResponse("UP", "UP"));
            }
        } catch (SQLException ignored) {
            // 对外只返回状态，不暴露数据库地址、账号或异常内容。
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse("DOWN", "DOWN"));
    }

    public record HealthResponse(String status, String database) {
    }
}
