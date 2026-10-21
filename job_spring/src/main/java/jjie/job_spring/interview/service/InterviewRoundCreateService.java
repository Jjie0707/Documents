package jjie.job_spring.interview.service;

import jjie.job_spring.interview.common.CreateInterviewRoundRequest;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.common.InterviewRoundResponse;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import jjie.job_spring.job.mapper.JobMapper;
import jjie.job_spring.interview.exception.DuplicateInterviewRoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewRoundCreateService {
    private final InterviewRoundMapper mapper;
    private final JobMapper jobs;

    public InterviewRoundCreateService(InterviewRoundMapper mapper, JobMapper jobs) {
        this.mapper = mapper;
        this.jobs = jobs;
    }

    @Transactional
    public InterviewRoundResponse createForUser(Long jobId, Long currentUserId, CreateInterviewRoundRequest request) {
        // 先锁当前用户的岗位；即使还没有轮次，也有一条稳定的父记录可以锁。
        // 锁一直持有到事务提交/回滚，使另一新增请求在查重之前等待。
        if (jobs.lockOwnedJob(jobId, currentUserId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "岗位不存在");
        }
        if (mapper.findExistingRoundId(jobId, currentUserId, request.roundNo()) != null) {
            throw new DuplicateInterviewRoundException();
        }
        InterviewRound round = new InterviewRound();
        round.setUserId(currentUserId);
        round.setJobApplicationId(jobId);
        round.setRoundNo(request.roundNo());
        round.setInterviewTime(request.interviewTime());
        round.setRoundType(request.roundType());
        round.setResult(request.result());
        round.setReview(request.review());
        // 显式插入新记录；重复轮次在此前拒绝，不更新或覆盖已有记录。
        mapper.insert(round);
        return InterviewRoundResponse.from(round);
    }
}
