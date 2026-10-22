-- 仅用于隔离 H2：故意在删除后段制造 RESTRICT 冲突，验证完整事务回滚。
CREATE TABLE IF NOT EXISTS round_delete_guard (
    round_id BIGINT PRIMARY KEY REFERENCES interview_rounds(id) ON DELETE RESTRICT
);
CREATE TABLE IF NOT EXISTS job_delete_guard (
    job_id BIGINT PRIMARY KEY REFERENCES job_applications(id) ON DELETE RESTRICT
);
