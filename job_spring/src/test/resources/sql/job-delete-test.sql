-- 仅用于删除接口的独立 H2 库，补充数据库草案的关联约束；不由应用启动执行。
-- 使用最少字段验证 RESTRICT，不实现阶段 4 的面试业务。
CREATE UNIQUE INDEX IF NOT EXISTS uk_delete_jobs_id_user ON job_applications(id, user_id);

CREATE TABLE IF NOT EXISTS interview_rounds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_application_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    UNIQUE (id, user_id),
    FOREIGN KEY (job_application_id, user_id)
        REFERENCES job_applications(id, user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS interview_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    interview_round_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    FOREIGN KEY (interview_round_id, user_id)
        REFERENCES interview_rounds(id, user_id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
