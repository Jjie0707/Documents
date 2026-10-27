-- 仅用于隔离 H2，验证单问题删除被约束拒绝时保留原记录。
CREATE TABLE IF NOT EXISTS question_delete_guard (
    question_id BIGINT PRIMARY KEY REFERENCES interview_questions(id) ON DELETE RESTRICT
);
