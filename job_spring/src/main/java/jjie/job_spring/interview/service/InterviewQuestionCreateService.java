package jjie.job_spring.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jjie.job_spring.interview.common.CreateInterviewQuestionRequest;
import jjie.job_spring.interview.common.InterviewQuestion;
import jjie.job_spring.interview.common.InterviewQuestionResponse;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.exception.InterviewRoundNotFoundException;
import jjie.job_spring.interview.mapper.InterviewQuestionMapper;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewQuestionCreateService {
    private final InterviewQuestionMapper questions;
    private final InterviewRoundMapper rounds;
    private final JobMapper jobs;

    public InterviewQuestionCreateService(InterviewQuestionMapper questions, InterviewRoundMapper rounds, JobMapper jobs) {
        this.questions = questions;
        this.rounds = rounds;
        this.jobs = jobs;
    }

    @Transactional
    public InterviewQuestionResponse createForUser(Long roundId, Long userId, CreateInterviewQuestionRequest request) {
        InterviewRound initial = rounds.selectOne(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getId, roundId).eq(InterviewRound::getUserId, userId));
        if (initial == null || jobs.lockOwnedJob(initial.getJobApplicationId(), userId) == null) {
            throw new InterviewRoundNotFoundException();
        }
        // 与轮次/岗位删除保持同一锁顺序；锁等待结束后必须当前读，不能只信最初的快照。
        InterviewRound current = rounds.selectOwnedRoundForUpdate(roundId, userId);
        if (current == null || !current.getJobApplicationId().equals(initial.getJobApplicationId())) {
            throw new InterviewRoundNotFoundException();
        }
        InterviewQuestion row = new InterviewQuestion();
        row.setUserId(userId);
        row.setInterviewRoundId(roundId);
        row.setQuestion(request.question());
        row.setAnswerNotes(request.answerNotes());
        row.setKnowledgeTopic(request.knowledgeTopic());
        row.setReviewNotes(request.reviewNotes());
        questions.insert(row);
        return InterviewQuestionResponse.from(row);
    }
}
