package jjie.job_spring.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.common.InterviewRoundResponse;
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

    public List<InterviewRoundResponse> listForUser(Long jobId, Long currentUserId) {
        // 空数组只表示自己的岗位暂无轮次；不存在和无权访问仍应为 404。
        jobs.findForUser(jobId, currentUserId);
        return mapper.selectList(new LambdaQueryWrapper<InterviewRound>()
                        .eq(InterviewRound::getJobApplicationId, jobId)
                        .eq(InterviewRound::getUserId, currentUserId)
                        .orderByAsc(InterviewRound::getRoundNo, InterviewRound::getId))
                .stream().map(InterviewRoundResponse::from).toList();
    }
}
