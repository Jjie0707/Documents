package jjie.job_spring.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jjie.job_spring.interview.common.InterviewQuestion;
import jjie.job_spring.interview.common.InterviewQuestionPageQuery;
import jjie.job_spring.interview.common.InterviewQuestionPageResponse;
import jjie.job_spring.interview.common.InterviewQuestionResponse;
import jjie.job_spring.interview.common.InterviewRound;
import jjie.job_spring.interview.exception.InterviewRoundNotFoundException;
import jjie.job_spring.interview.mapper.InterviewQuestionMapper;
import jjie.job_spring.interview.mapper.InterviewRoundMapper;
import org.springframework.stereotype.Service;

@Service
public class InterviewQuestionReadService {
    private static final int PAGE_SIZE = 10;
    private final InterviewQuestionMapper questions;
    private final InterviewRoundMapper rounds;

    public InterviewQuestionReadService(InterviewQuestionMapper questions, InterviewRoundMapper rounds) {
        this.questions = questions;
        this.rounds = rounds;
    }

    public InterviewQuestionPageResponse listForUser(Long roundId, Long userId, InterviewQuestionPageQuery query) {
        if (rounds.selectOne(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getId, roundId).eq(InterviewRound::getUserId, userId)) == null) {
            throw new InterviewRoundNotFoundException();
        }
        long total = questions.selectCount(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewRoundId, roundId).eq(InterviewQuestion::getUserId, userId));
        long totalPages = total / PAGE_SIZE + (total % PAGE_SIZE == 0 ? 0 : 1);
        long requestedPage = query.page();
        if (query.questionId() != null && questions.selectOne(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, query.questionId()).eq(InterviewQuestion::getUserId, userId)
                .eq(InterviewQuestion::getInterviewRoundId, roundId)) != null) {
            requestedPage = questions.countBeforeQuestion(roundId, userId, query.questionId()) / PAGE_SIZE + 1;
        }
        int page = (int) Math.min(requestedPage, Math.max(1, totalPages));
        var items = questions.selectPageRows(roundId, userId, PAGE_SIZE, (page - 1L) * PAGE_SIZE)
                .stream().map(InterviewQuestionResponse::from).toList();
        return new InterviewQuestionPageResponse(items, page, PAGE_SIZE, total, totalPages);
    }
}
