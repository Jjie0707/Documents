package jjie.job_spring.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.common.InterviewRoundResponse;
import jjie.job_spring.interview.common.InterviewRoundPageQuery;
import jjie.job_spring.interview.common.InterviewRoundPageResponse;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import jjie.job_spring.job.service.JobReadService;
import org.springframework.stereotype.Service;

@Service
public class InterviewRoundReadService {
    private final InterviewRoundMapper mapper;
    private final JobReadService jobs;

    public InterviewRoundReadService(InterviewRoundMapper mapper, JobReadService jobs) {
        this.mapper = mapper;
        this.jobs = jobs;
    }

    public InterviewRoundPageResponse listForUser(Long jobId, Long currentUserId, InterviewRoundPageQuery query) {
        jobs.findForUser(jobId, currentUserId);
        long total = mapper.selectCount(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getJobApplicationId, jobId).eq(InterviewRound::getUserId, currentUserId));
        long requestedPage = query.page();
        if (query.roundId() != null) {
            // 新增或改号后按记录 ID 定位实际页，不能把轮次号当成页码。
            InterviewRound target = mapper.selectOne(new LambdaQueryWrapper<InterviewRound>()
                    .eq(InterviewRound::getId, query.roundId())
                    .eq(InterviewRound::getJobApplicationId, jobId).eq(InterviewRound::getUserId, currentUserId));
            if (target != null) {
                requestedPage = mapper.countBeforeRound(jobId, currentUserId, target.getRoundNo(), target.getId()) + 1;
            }
        }
        int page = (int) Math.min(requestedPage, Math.max(1, total));
        InterviewRound row = total == 0 ? null : mapper.selectPageRow(jobId, currentUserId, page - 1L);
        List<InterviewRoundResponse> items = row == null ? List.of() : List.of(InterviewRoundResponse.from(row));
        return new InterviewRoundPageResponse(items, page, 1, total, total);
    }
}
