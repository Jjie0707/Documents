-- Formal migration verification for MySQL 9.3 source and MySQL 8.4 target.
-- Select the database with the client's --database option. Contains no row data.
-- Run with --batch --skip-column-names --show-warnings; keep output private.
-- Only session settings and read-only queries; no schema or row changes.
-- Migration-window snapshot comparison only: after target business writes,
-- differences from the old source are expected and do not authorize reimport.
SET SESSION group_concat_max_len = 1048576;
SET SESSION information_schema_stats_expiry = 0;
SET SESSION time_zone = '+08:00';
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;

SELECT 'identity', 'mysql_version', VERSION(), DATABASE();

-- Metadata signatures omit the server version, physical sizes and statistics.
SELECT 'schema' AS check_type, 'tables' AS object_name, COUNT(*) AS item_count,
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COLLATION, AUTO_INCREMENT
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY TABLE_NAME SEPARATOR ''), 256) AS signature
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
UNION ALL
SELECT 'schema', 'columns', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
         COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME,
         GENERATION_EXPRESSION
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY TABLE_NAME, ORDINAL_POSITION SEPARATOR ''), 256)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
UNION ALL
SELECT 'schema', 'indexes', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME,
         COLLATION, SUB_PART, INDEX_TYPE, IS_VISIBLE, EXPRESSION
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX SEPARATOR ''), 256)
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
UNION ALL
SELECT 'schema', 'constraints', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE, ENFORCED
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY TABLE_NAME, CONSTRAINT_NAME SEPARATOR ''), 256)
FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
UNION ALL
SELECT 'schema', 'constraint_columns', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, ORDINAL_POSITION,
         POSITION_IN_UNIQUE_CONSTRAINT, REFERENCED_TABLE_NAME,
         REFERENCED_COLUMN_NAME
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION SEPARATOR ''), 256)
FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE()
UNION ALL
SELECT 'schema', 'foreign_keys', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         TABLE_NAME, CONSTRAINT_NAME, UNIQUE_CONSTRAINT_NAME,
         REFERENCED_TABLE_NAME, MATCH_OPTION, UPDATE_RULE, DELETE_RULE
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY TABLE_NAME, CONSTRAINT_NAME SEPARATOR ''), 256)
FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
UNION ALL
SELECT 'schema', 'checks', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         tc.TABLE_NAME, cc.CONSTRAINT_NAME, cc.CHECK_CLAUSE, tc.ENFORCED
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY tc.TABLE_NAME, cc.CONSTRAINT_NAME SEPARATOR ''), 256)
FROM information_schema.CHECK_CONSTRAINTS cc
JOIN information_schema.TABLE_CONSTRAINTS tc
  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
 AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
 AND tc.CONSTRAINT_TYPE = 'CHECK'
WHERE cc.CONSTRAINT_SCHEMA = DATABASE();

-- Hash every application column. JSON distinguishes NULL, empty text and types.
SELECT 'data', 'users', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         id, username, password_hash, created_at, updated_at
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY id SEPARATOR ''), 256)
FROM users
UNION ALL
SELECT 'data', 'job_applications', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         id, user_id, company_name, job_title, city, source_platform,
         original_url, salary, responsibilities, requirements,
         education_requirement, application_deadline, status,
         created_at, updated_at
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY id SEPARATOR ''), 256)
FROM job_applications
UNION ALL
SELECT 'data', 'interview_rounds', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         id, user_id, job_application_id, round_no, interview_time,
         round_type, result, review, created_at, updated_at
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY id SEPARATOR ''), 256)
FROM interview_rounds
UNION ALL
SELECT 'data', 'interview_questions', COUNT(*),
       SHA2(GROUP_CONCAT(SHA2(CAST(JSON_ARRAY(
         id, user_id, interview_round_id, question, answer_notes,
         knowledge_topic, review_notes, created_at, updated_at
       ) AS CHAR CHARACTER SET utf8mb4), 256)
       ORDER BY id SEPARATOR ''), 256)
FROM interview_questions;

-- Every integrity result must be zero, even when both sides match.
SELECT 'integrity', 'database_name', IF(DATABASE() = 'job_tracker', 0, 1), ''
UNION ALL
SELECT 'integrity', 'unexpected_tables', COUNT(*), ''
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME NOT IN ('users', 'job_applications', 'interview_rounds', 'interview_questions')
UNION ALL
SELECT 'integrity', 'missing_tables', 4 - COUNT(*), ''
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN ('users', 'job_applications', 'interview_rounds', 'interview_questions')
UNION ALL
SELECT 'integrity', 'extra_objects',
       (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE())
     + (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE())
     + (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA = DATABASE()), ''
UNION ALL
SELECT 'integrity', 'foreign_key_checks_disabled', IF(@@SESSION.foreign_key_checks = 1, 0, 1), ''
UNION ALL
SELECT 'integrity', 'jobs_without_user', COUNT(*), ''
FROM job_applications j LEFT JOIN users u ON u.id = j.user_id
WHERE u.id IS NULL
UNION ALL
SELECT 'integrity', 'rounds_without_matching_job', COUNT(*), ''
FROM interview_rounds r
LEFT JOIN job_applications j ON j.id = r.job_application_id AND j.user_id = r.user_id
WHERE j.id IS NULL
UNION ALL
SELECT 'integrity', 'questions_without_matching_round', COUNT(*), ''
FROM interview_questions q
LEFT JOIN interview_rounds r ON r.id = q.interview_round_id AND r.user_id = q.user_id
WHERE r.id IS NULL
UNION ALL
SELECT 'integrity', 'invalid_status', COUNT(*), ''
FROM job_applications
WHERE status IS NULL OR status NOT IN
  ('TO_APPLY', 'APPLIED', 'WRITTEN_TEST', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN')
UNION ALL
SELECT 'integrity', 'invalid_round_no', COUNT(*), ''
FROM interview_rounds WHERE round_no IS NULL OR round_no < 1;

COMMIT;
