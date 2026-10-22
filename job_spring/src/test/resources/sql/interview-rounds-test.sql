-- 仅用于隔离 H2；与 MySQL 轮次设计保持相同字段和关键约束，不修改用户数据库。
CREATE UNIQUE INDEX IF NOT EXISTS uk_interview_jobs_id_user ON job_applications(id, user_id);
CREATE TABLE IF NOT EXISTS interview_rounds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_application_id BIGINT NOT NULL,
    round_no INT NOT NULL CHECK (round_no >= 1),
    interview_time TIMESTAMP(3),
    round_type VARCHAR(50),
    result VARCHAR(100),
    review TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, user_id),
    FOREIGN KEY (job_application_id, user_id)
        REFERENCES job_applications(id, user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_interview_job_user_no ON interview_rounds(job_application_id, user_id, round_no);

CREATE TABLE IF NOT EXISTS interview_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    interview_round_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    answer_notes TEXT,
    knowledge_topic VARCHAR(100),
    review_notes TEXT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (interview_round_id, user_id)
        REFERENCES interview_rounds(id, user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
