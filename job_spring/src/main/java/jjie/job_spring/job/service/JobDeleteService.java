package jjie.job_spring.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jjie.job_spring.job.common.JobApplication;
import jjie.job_spring.job.mapper.JobMapper;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import jjie.job_spring.interview.mapper.InterviewQuestionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobDeleteService {
    private final JobMapper mapper;
    private final InterviewRoundMapper rounds;
    private final InterviewQuestionMapper questions;

    public JobDeleteService(JobMapper mapper, InterviewRoundMapper rounds, InterviewQuestionMapper questions) {
        this.mapper = mapper;
        this.rounds = rounds;
        this.questions = questions;
    }

    @Transactional
    public void deleteForUser(Long id, Long currentUserId) {
        // 先确认身份归属并持有父岗位锁，越权/不存在时不能先清理任何子记录。
        if (mapper.lockOwnedJob(id, currentUserId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "岗位不存在");
        }
        // 保留 RESTRICT，通过业务事务从子到父删除；任一步失败全部回滚。
        questions.deleteForJob(id, currentUserId);
        rounds.delete(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getJobApplicationId, id).eq(InterviewRound::getUserId, currentUserId));
        // 删除语句自身限定归属，不信任前端传入的用户 ID，也不做无条件 deleteById。
        int deleted = mapper.delete(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, id)
                .eq(JobApplication::getUserId, currentUserId));
        // DELETE 的 0 表示本次确实未删除记录，与“相同值 UPDATE 返回 0”不同。
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "岗位不存在");
        }
    }
}
