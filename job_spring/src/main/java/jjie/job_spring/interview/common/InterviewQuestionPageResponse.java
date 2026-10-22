package jjie.job_spring.interview.common;

import java.util.List;

public record InterviewQuestionPageResponse(List<InterviewQuestionResponse> items, int page, int size,
                                            long total, long totalPages) { }
