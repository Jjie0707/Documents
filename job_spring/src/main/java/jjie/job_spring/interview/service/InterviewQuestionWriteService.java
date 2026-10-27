package jjie.job_spring.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jjie.job_spring.interview.common.InterviewQuestion;
import jjie.job_spring.interview.common.InterviewQuestionResponse;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.common.UpdateInterviewQuestionRequest;
import jjie.job_spring.interview.exception.InterviewQuestionNotFoundException;
import jjie.job_spring.interview.mapper.InterviewQuestionMapper;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewQuestionWriteService {
    private final InterviewQuestionMapper questions;
    private final InterviewRoundMapper rounds;
    private final JobMapper jobs;

    public InterviewQuestionWriteService(InterviewQuestionMapper questions, InterviewRoundMapper rounds, JobMapper jobs) {
        this.questions = questions;
        this.rounds = rounds;
        this.jobs = jobs;
    }

    @Transactional
    public InterviewQuestionResponse updateForUser(Long id, Long userId, UpdateInterviewQuestionRequest request) {
        InterviewQuestion current = lockOwnedQuestion(id, userId);
        // 显式 set，包含 NULL；不能使用会跳过空值的实体更新策略。
        questions.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, id).eq(InterviewQuestion::getUserId, userId)
                .eq(InterviewQuestion::getInterviewRoundId, current.getInterviewRoundId())
                .set(InterviewQuestion::getQuestion, request.question())
                .set(InterviewQuestion::getAnswerNotes, request.answerNotes())
                .set(InterviewQuestion::getKnowledgeTopic, request.knowledgeTopic())
                .set(InterviewQuestion::getReviewNotes, request.reviewNotes()));
        // 同内容保存也成功，不把 MySQL 的 UPDATE 影响行数 0 当作不存在。
        return InterviewQuestionResponse.from(questions.selectOwnedForUpdate(id, userId));
    }

    @Transactional
    public void deleteForUser(Long id, Long userId) {
        InterviewQuestion current = lockOwnedQuestion(id, userId);
        int deleted = questions.delete(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, id).eq(InterviewQuestion::getUserId, userId)
                .eq(InterviewQuestion::getInterviewRoundId, current.getInterviewRoundId()));
        if (deleted == 0) throw new InterviewQuestionNotFoundException();
    }

    private InterviewQuestion lockOwnedQuestion(Long id, Long userId) {
        // 初次读取只用于定位父级；拿到父岗位锁后再当前读轮次和问题。
        InterviewQuestion initial = questions.selectOne(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, id).eq(InterviewQuestion::getUserId, userId));
        if (initial == null) throw new InterviewQuestionNotFoundException();
        InterviewRound parent = rounds.selectOne(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getId, initial.getInterviewRoundId()).eq(InterviewRound::getUserId, userId));
        if (parent == null || jobs.lockOwnedJob(parent.getJobApplicationId(), userId) == null) {
            throw new InterviewQuestionNotFoundException();
        }
        InterviewRound round = rounds.selectOwnedRoundForUpdate(parent.getId(), userId);
        if (round == null || !round.getJobApplicationId().equals(parent.getJobApplicationId())) {
            throw new InterviewQuestionNotFoundException();
        }
        InterviewQuestion current = questions.selectOwnedForUpdate(id, userId);
        if (current == null || !current.getInterviewRoundId().equals(round.getId())) {
            throw new InterviewQuestionNotFoundException();
        }
        return current;
    }
}
