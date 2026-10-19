-- 可选的手动验收数据，不由应用自动执行。
-- 先通过注册页面创建 phase2_demo_a、phase2_demo_b 两个临时账号。
-- 密码通过页面自行设置，不写入本脚本。若使用了其他测试用户名，请修改下方两个变量。
-- 仅添加这两个账号的演示岗位，不删除、不修改已有记录。可重复执行。
USE job_tracker;
SET @phase2_demo_a = 'phase2_demo_a';
SET @phase2_demo_b = 'phase2_demo_b';

START TRANSACTION;
INSERT INTO job_applications (user_id, company_name, job_title, city, source_platform, status)
SELECT u.id, '阶段2隔离验证A', '仅A可见的测试岗位', '测试城市', 'PHASE2_MANUAL_TEST', 'TO_APPLY'
FROM users u
LEFT JOIN job_applications j ON j.user_id = u.id
    AND j.company_name = '阶段2隔离验证A' AND j.source_platform = 'PHASE2_MANUAL_TEST'
WHERE u.username = @phase2_demo_a AND j.id IS NULL;

INSERT INTO job_applications (user_id, company_name, job_title, city, source_platform, status)
SELECT u.id, '阶段2隔离验证B', '仅B可见的测试岗位', '测试城市', 'PHASE2_MANUAL_TEST', 'APPLIED'
FROM users u
LEFT JOIN job_applications j ON j.user_id = u.id
    AND j.company_name = '阶段2隔离验证B' AND j.source_platform = 'PHASE2_MANUAL_TEST'
WHERE u.username = @phase2_demo_b AND j.id IS NULL;
COMMIT;

-- 记录两条演示岗位的 ID，用于检查修改 URL 后是否能越权读取。
SELECT u.username, j.id AS job_id, j.job_title
FROM job_applications j JOIN users u ON u.id = j.user_id
WHERE u.username IN (@phase2_demo_a, @phase2_demo_b)
  AND j.source_platform = 'PHASE2_MANUAL_TEST';
