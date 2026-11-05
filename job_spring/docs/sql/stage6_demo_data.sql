-- 先在隔离库的应用页面注册 stage6_demo_a 和 stage6_demo_b。
-- 本文件手动执行，仅向 job_tracker_demo 写入。请在完整导入结束后再编辑样例。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @demo_a = 'stage6_demo_a';
SET @demo_b = 'stage6_demo_b';
SET @demo_source = '阶段六虚构演示';
SET @demo_timer = 'https://jobs.example.invalid/timer-java';
SET @demo_cloud = 'https://jobs.example.invalid/cloud-java';
SET @demo_lock = GET_LOCK('job_tracker_demo.stage6_seed', 5);

START TRANSACTION;

SET @demo_ready = (
    SELECT @demo_lock = 1 AND COUNT(*) = 2
    FROM job_tracker_demo.users WHERE username IN (@demo_a, @demo_b)
);

INSERT INTO job_tracker_demo.job_applications
    (user_id, company_name, job_title, city, source_platform, original_url, salary,
     responsibilities, requirements, education_requirement, application_deadline, status)
SELECT u.id, sample.company, sample.title, sample.city, @demo_source, sample.url,
       sample.salary, sample.responsibilities, sample.requirements, sample.education,
       sample.deadline, sample.status
FROM (
    SELECT @demo_a AS username, '北屿时序实验室' AS company, 'Java 后端工程师' AS title,
           '云屿市' AS city, @demo_timer AS url, '18–24K' AS salary,
           '维护任务调度服务，补充失败重试和幂等校验。' AS responsibilities,
           '熟悉 Spring Boot、MySQL 事务和 Redis。' AS requirements,
           '本科及以上' AS education, NULL AS deadline, 'INTERVIEWING' AS status
    UNION ALL
    SELECT @demo_a, '雾塔云图科技', '平台后端工程师', '雾港市', @demo_cloud, '16–22K',
           '维护 API 和权限控制，记录接口性能指标。', '了解 Java、SQL 和 HTTP。',
           '本科及以上', NULL, 'OFFER'
    UNION ALL
    SELECT @demo_a, '青穗微光工作室', '应用开发实习生', '云屿市',
           'https://jobs.example.invalid/application-intern', NULL,
           '为内部工具补充搜索和分页。', '掌握 Java 集合和关系数据库基础。',
           NULL, CURRENT_DATE + INTERVAL 14 DAY, 'TO_APPLY'
    UNION ALL
    SELECT @demo_a, '纸舟数据工坊', '数据服务开发工程师', '星桥市',
           'https://jobs.example.invalid/data-service', NULL,
           '整理数据导入接口并校验输入。', '了解 JDBC、索引和单元测试。',
           NULL, NULL, 'APPLIED'
    UNION ALL
    SELECT @demo_b, '澄岬观测站', '接口测试工程师', '雾港市',
           'https://jobs.example.invalid/api-test', NULL,
           '为 REST API 编写回归用例。', '能描述状态码、会话和边界条件。',
           NULL, NULL, 'APPLIED'
) AS sample
JOIN job_tracker_demo.users u ON u.username = sample.username
LEFT JOIN job_tracker_demo.job_applications existing
    ON existing.user_id = u.id AND existing.source_platform = @demo_source AND existing.original_url = sample.url
WHERE @demo_ready AND existing.id IS NULL;

INSERT INTO job_tracker_demo.interview_rounds
    (user_id, job_application_id, round_no, interview_time, round_type, result, review)
SELECT j.user_id, j.id, sample.round_no, sample.interview_time, sample.round_type, sample.result, sample.review
FROM (
    SELECT @demo_timer AS url, 1 AS round_no, '2026-11-02 10:00:00' AS interview_time,
           '技术一面' AS round_type, '通过' AS result, '补充说明事务回滚和重复提交。' AS review
    UNION ALL
    SELECT @demo_timer, 2, '2026-11-04 14:30:00', '技术二面', '待反馈', '复盘任务调度的失败恢复。'
    UNION ALL
    SELECT @demo_cloud, 1, '2026-10-29 15:00:00', '技术面', '通过', '记录分页索引的改进思路。'
) AS sample
JOIN job_tracker_demo.job_applications j ON j.original_url = sample.url AND j.source_platform = @demo_source
JOIN job_tracker_demo.users u ON u.id = j.user_id AND u.username = @demo_a
LEFT JOIN job_tracker_demo.interview_rounds existing
    ON existing.job_application_id = j.id AND existing.user_id = j.user_id AND existing.round_no = sample.round_no
WHERE @demo_ready AND existing.id IS NULL;

INSERT INTO job_tracker_demo.interview_questions
    (user_id, interview_round_id, question, answer_notes, knowledge_topic, review_notes)
SELECT r.user_id, r.id, sample.question, sample.answer_notes, sample.topic, sample.review_notes
FROM (
    SELECT @demo_timer AS url, 1 AS round_no, '一笔事务里的第二条写入失败时会怎样？' AS question,
           '先确认两个写操作是否处于同一事务，再说明异常传播和回滚条件。' AS answer_notes,
           '事务' AS topic, '下次用岗位和面试轮次两个表举例。' AS review_notes
    UNION ALL
    SELECT @demo_timer, 1, '如何防止一次提交生成两条重复记录？',
           '用幂等键识别同一次请求，必要时让数据库唯一约束兜底。', '幂等', '区分前端防连点和后端防重复。'
    UNION ALL
    SELECT @demo_timer, 2, '任务重试时如何避免再次执行已经成功的步骤？',
           '记录执行状态，按步骤确认结果，再重试可安全重复的操作。', '幂等', '补上执行超时后状态未知的情况。'
    UNION ALL
    SELECT @demo_cloud, 1, '列表同时按状态筛选和时间排序时，怎样设计索引？',
           '结合查询条件和排序列设计复合索引，再用执行计划验证。', '索引', '说明翻页时如何保持稳定排序。'
) AS sample
JOIN job_tracker_demo.job_applications j ON j.original_url = sample.url AND j.source_platform = @demo_source
JOIN job_tracker_demo.users u ON u.id = j.user_id AND u.username = @demo_a
JOIN job_tracker_demo.interview_rounds r
    ON r.job_application_id = j.id AND r.user_id = j.user_id AND r.round_no = sample.round_no
LEFT JOIN job_tracker_demo.interview_questions existing
    ON existing.interview_round_id = r.id AND existing.user_id = r.user_id AND existing.question = sample.question
WHERE @demo_ready AND existing.id IS NULL;

COMMIT;

SELECT CASE
    WHEN COALESCE(@demo_lock, 0) <> 1 THEN 'IMPORT_BUSY'
    WHEN @demo_ready THEN 'READY'
    ELSE 'MISSING_DEMO_ACCOUNTS'
END AS demo_status;
DO RELEASE_LOCK('job_tracker_demo.stage6_seed');
