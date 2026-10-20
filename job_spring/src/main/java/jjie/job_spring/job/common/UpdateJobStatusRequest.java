package jjie.job_spring.job.common;

import jakarta.validation.constraints.NotNull;

public record UpdateJobStatusRequest(
        @NotNull(message = "投递状态不能为空") JobStatus status
) { }
