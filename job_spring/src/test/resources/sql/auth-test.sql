-- 只用于独立 H2 内存测试库；与生产数据库无关。
CREATE TABLE IF NOT EXISTS job_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    job_title VARCHAR(200) NOT NULL,
    city VARCHAR(100),
    source_platform VARCHAR(100),
    original_url VARCHAR(2048),
    salary VARCHAR(100),
    responsibilities TEXT,
    requirements TEXT,
    education_requirement VARCHAR(100),
    application_deadline DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'TO_APPLY',
    FOREIGN KEY (user_id) REFERENCES users(id)
);
