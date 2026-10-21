package jjie.job_spring.job.common;

import java.util.List;

public record JobPageResponse(List<JobResponse> items, int page, int size, long total, long totalPages) {
    public JobPageResponse {
        items = List.copyOf(items);
    }
}
