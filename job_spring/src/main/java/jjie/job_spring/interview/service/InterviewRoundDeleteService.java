package jjie.job_spring.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.exception.InterviewRoundNotFoundException;
import jjie.job_spring.interview.mapper.InterviewQuestionMapper;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewRoundDeleteService {
    private final InterviewRoundMapper rounds;
    private final InterviewQuestionMapper questions;
    private final JobMapper jobs;

    public InterviewRoundDeleteService(InterviewRoundMapper rounds, InterviewQuestionMapper questions, JobMapper jobs) {
        this.rounds = rounds;
        this.questions = questions;
        this.jobs = jobs;
    }

    @Transactional
    public void deleteForUser(Long id, Long currentUserId) {
        InterviewRound initial = rounds.selectOne(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getId, id).eq(InterviewRound::getUserId, currentUserId));
        if (initial == null || jobs.lockOwnedJob(initial.getJobApplicationId(), currentUserId) == null) {
            throw new InterviewRoundNotFoundException();
        }
        // 与新增/编辑统一先锁岗位，再当前读轮次。轮次编号不参与删除目标定位。
        InterviewRound current = rounds.selectOwnedRoundForUpdate(id, currentUserId);
        if (current == null || !current.getJobApplicationId().equals(initial.getJobApplicationId())) {
            throw new InterviewRoundNotFoundException();
        }
        questions.deleteForRound(id, currentUserId);
        int deleted = rounds.delete(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getId, id).eq(InterviewRound::getUserId, currentUserId)
                .eq(InterviewRound::getJobApplicationId, current.getJobApplicationId()));
        if (deleted == 0) throw new InterviewRoundNotFoundException();
        // 若轮次删除失败，上面的问题删除也随整个事务回滚。
    }
}
