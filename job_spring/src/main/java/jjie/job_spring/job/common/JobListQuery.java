package jjie.job_spring.job.common;

import org.hibernate.validator.constraints.CodePointLength;

public record JobListQuery(
        @CodePointLength(max = 100, message = "搜索关键词不能超过100个字符")
        String keyword,
        JobStatus status
) {
    public JobListQuery {
        if (keyword != null) {
            keyword = keyword.strip();
            if (keyword.isEmpty()) keyword = null;
        }
    }

    @Override
    public String toString() {
        return "JobListQuery[content=REDACTED]";
    }
}
