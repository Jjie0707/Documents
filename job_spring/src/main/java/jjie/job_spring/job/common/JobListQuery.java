package jjie.job_spring.job.common;

import org.hibernate.validator.constraints.CodePointLength;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record JobListQuery(
        @CodePointLength(max = 100, message = "搜索关键词不能超过100个字符")
        String keyword,
        JobStatus status,
        @Min(value = 1, message = "页码必须大于等于1") Integer page,
        @Min(value = 1, message = "每页条数必须在1到50之间")
        @Max(value = 50, message = "每页条数必须在1到50之间") Integer size
) {
    public JobListQuery {
        if (page == null) page = 1;
        if (size == null) size = 10;
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
