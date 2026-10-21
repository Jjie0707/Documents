package jjie.job_spring.interview.common;

import java.util.List;

// 每页固定一条。page 是记录所在页，roundNo 是业务轮次号，两者不能混用。
public record InterviewRoundPageResponse(List<InterviewRoundResponse> items, int page, int size,
                                          long total, long totalPages) {
    public InterviewRoundPageResponse {
        items = List.copyOf(items);
    }
}
