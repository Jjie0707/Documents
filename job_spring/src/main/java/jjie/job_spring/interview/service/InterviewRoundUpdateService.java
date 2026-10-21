package jjie.job_spring.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.common.InterviewRoundResponse;
import jjie.job_spring.interview.common.UpdateInterviewRoundRequest;
import jjie.job_spring.interview.exception.DuplicateInterviewRoundException;
import jjie.job_spring.interview.exception.InterviewRoundNotFoundException;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewRoundUpdateService {
    private final InterviewRoundMapper mapper;
    private final JobMapper jobs;

    public InterviewRoundUpdateService(InterviewRoundMapper mapper, JobMapper jobs) {
        this.mapper = mapper;
        this.jobs = jobs;
    }

    @Transactional
    public InterviewRoundResponse updateForUser(Long id, Long currentUserId, UpdateInterviewRoundRequest request) {
        // 这里只为查到所属岗位；取得父锁后必须用当前读重新确认目标记录。
        InterviewRound initial = mapper.selectOne(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getId, id).eq(InterviewRound::getUserId, currentUserId));
        if (initial == null || jobs.lockOwnedJob(initial.getJobApplicationId(), currentUserId) == null) {
            throw new InterviewRoundNotFoundException();
        }
        InterviewRound current = mapper.selectOwnedRoundForUpdate(id, currentUserId);
        if (current == null || !current.getJobApplicationId().equals(initial.getJobApplicationId())) {
            throw new InterviewRoundNotFoundException();
        }
        // 同号保存是修改当前记录；历史重复也允许先编辑内容，再由用户逐条改号。
        if (!current.getRoundNo().equals(request.roundNo())
                && mapper.findExistingRoundId(current.getJobApplicationId(), currentUserId, request.roundNo()) != null) {
            throw new DuplicateInterviewRoundException();
        }
        mapper.update(null, new LambdaUpdateWrapper<InterviewRound>()
                .eq(InterviewRound::getId, id).eq(InterviewRound::getUserId, currentUserId)
                .eq(InterviewRound::getJobApplicationId, current.getJobApplicationId())
                .set(InterviewRound::getRoundNo, request.roundNo())
                .set(InterviewRound::getInterviewTime, request.interviewTime())
                .set(InterviewRound::getRoundType, request.roundType())
                .set(InterviewRound::getResult, request.result())
                .set(InterviewRound::getReview, request.review()));
        // 重复提交相同内容也成功；不使用 UPDATE 影响行数判断是否存在。
        return InterviewRoundResponse.from(mapper.selectOwnedRoundForUpdate(id, currentUserId));
    }
}
