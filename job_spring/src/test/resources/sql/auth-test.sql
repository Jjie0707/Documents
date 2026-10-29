-- 只用于独立 H2 内存测试库；与生产数据库无关。
CREATE TABLE IF NOT EXISTS job_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    job_title VARCHAR(200) NOT NULL,
    city VARCHAR(100),
    -- H2 按 UTF-16 单元计算 VARCHAR 长度；为 MySQL utf8mb4 的码点上限留出双倍空间。
    -- 业务的 100/2048 码点上限仍由请求 DTO 和边界测试严格约束。
    source_platform VARCHAR(200),
    original_url VARCHAR(4096),
    salary VARCHAR(100),
    responsibilities TEXT,
    requirements TEXT,
    education_requirement VARCHAR(200),
    application_deadline DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'TO_APPLY',
    FOREIGN KEY (user_id) REFERENCES users(id)
);
