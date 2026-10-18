-- 岗位投递与面试记录工具：数据库初稿。
-- 目标：MySQL 8.0.16+（含 8.4），InnoDB，utf8mb4。
-- 请先在 IDEA 数据库控制台选择自己的开发数据库，再手动执行。
-- 不创建数据库、不删除已有表、不插入账号；本文件不会由 Spring Boot 自动执行。
-- 待业务确认的约定：用户名登录、以下七种岗位状态、物理删除。
-- 外键使用 RESTRICT：有子记录时拒绝直接删除，避免隐式删除面试内容。
-- DATETIME 时间字段暂按 Asia/Shanghai 解释；应用和数据库会话应保持一致。
CREATE DATABASE job_tracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

use job_tracker;
CREATE TABLE users (
                       id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                       username VARCHAR(50) NOT NULL COMMENT '登录用户名，按本表排序规则不区分大小写',
                       password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希，禁止保存明文密码',
                       created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                       updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           ON UPDATE CURRENT_TIMESTAMP(3),
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='用户';

CREATE TABLE job_applications (
                                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '岗位投递记录ID',
                                  user_id BIGINT NOT NULL COMMENT '所属用户',
                                  company_name VARCHAR(200) NOT NULL COMMENT '公司名称',
                                  job_title VARCHAR(200) NOT NULL COMMENT '岗位名称',
                                  city VARCHAR(100) NULL COMMENT '城市',
                                  source_platform VARCHAR(100) NULL COMMENT '来源平台',
                                  original_url VARCHAR(2048) NULL COMMENT '原始招聘链接',
                                  salary VARCHAR(100) NULL COMMENT '薪资原文，例如15-20K·13薪',
                                  responsibilities TEXT NULL COMMENT '岗位职责',
                                  requirements TEXT NULL COMMENT '任职要求',
                                  education_requirement VARCHAR(100) NULL COMMENT '学历要求',
                                  application_deadline DATE NULL COMMENT '投递截止日期',
                                  status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                                      DEFAULT 'TO_APPLY'
                                      COMMENT 'TO_APPLY/APPLIED/WRITTEN_TEST/INTERVIEWING/OFFER/REJECTED/WITHDRAWN',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uk_jobs_id_user (id, user_id),
                                  KEY idx_jobs_user_created (user_id, created_at, id),
                                  KEY idx_jobs_user_status_created (user_id, status, created_at, id),
                                  CONSTRAINT fk_jobs_user FOREIGN KEY (user_id)
                                      REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                  CONSTRAINT chk_jobs_status CHECK (status IN (
                                                                               'TO_APPLY', 'APPLIED', 'WRITTEN_TEST', 'INTERVIEWING',
                                                                               'OFFER', 'REJECTED', 'WITHDRAWN'
                                      ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='岗位投递记录';

CREATE TABLE interview_rounds (
                                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '面试轮次ID',
                                  user_id BIGINT NOT NULL COMMENT '所属用户，必须与岗位一致',
                                  job_application_id BIGINT NOT NULL COMMENT '所属岗位投递记录',
                                  round_no INT NOT NULL COMMENT '第几轮面试，从1开始',
                                  interview_time DATETIME(3) NULL COMMENT '面试时间',
                                  round_type VARCHAR(50) NULL COMMENT '轮次类型，例如技术面或HR面',
                                  result VARCHAR(100) NULL COMMENT '面试结果，暂用自由文本',
                                  review TEXT NULL COMMENT '本轮整体复盘',
                                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uk_rounds_id_user (id, user_id),
                                  KEY idx_rounds_job_user_no (job_application_id, user_id, round_no),
                                  KEY idx_rounds_user (user_id),
                                  CONSTRAINT fk_rounds_job_owner FOREIGN KEY (job_application_id, user_id)
                                      REFERENCES job_applications (id, user_id)
                                      ON DELETE RESTRICT ON UPDATE RESTRICT,
                                  CONSTRAINT chk_rounds_no CHECK (round_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='面试轮次';

CREATE TABLE interview_questions (
                                     id BIGINT NOT NULL AUTO_INCREMENT COMMENT '面试问题ID',
                                     user_id BIGINT NOT NULL COMMENT '所属用户，必须与面试轮次一致',
                                     interview_round_id BIGINT NOT NULL COMMENT '所属面试轮次',
                                     question TEXT NOT NULL COMMENT '问题内容',
                                     answer_notes TEXT NULL COMMENT '自己的回答要点',
                                     knowledge_topic VARCHAR(100) NULL COMMENT '知识主题，暂用单个文本字段',
                                     review_notes TEXT NULL COMMENT '问题复盘备注',
                                     created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                     updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                         ON UPDATE CURRENT_TIMESTAMP(3),
                                     PRIMARY KEY (id),
                                     KEY idx_questions_round_user (interview_round_id, user_id),
                                     KEY idx_questions_user_topic (user_id, knowledge_topic),
                                     CONSTRAINT fk_questions_round_owner FOREIGN KEY (interview_round_id, user_id)
                                         REFERENCES interview_rounds (id, user_id)
                                         ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    COMMENT='面试问题与复盘';