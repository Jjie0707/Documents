package jjie.job_spring.job.common;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum JobStatus {
    TO_APPLY,
    APPLIED,
    WRITTEN_TEST,
    INTERVIEWING,
    OFFER,
    REJECTED,
    WITHDRAWN;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static JobStatus fromJson(String value) {
        // 精确匹配状态代码，避免把数字按枚举序号接受，也不猜测大小写或空白。
        return JobStatus.valueOf(value);
    }
}
