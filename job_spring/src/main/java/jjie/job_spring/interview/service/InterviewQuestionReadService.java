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
        long total = questions.selectCount(conditions(roundId, userId, query.keyword(), query.topic()));
        long totalPages = total / PAGE_SIZE + (total % PAGE_SIZE == 0 ? 0 : 1);
        long requestedPage = query.page();
        if (query.questionId() != null && questions.selectOne(conditions(roundId, userId, query.keyword(), query.topic())
                .eq(InterviewQuestion::getId, query.questionId())) != null) {
            requestedPage = questions.selectCount(conditions(roundId, userId, query.keyword(), query.topic())
                    .gt(InterviewQuestion::getId, query.questionId())) / PAGE_SIZE + 1;
        }
        int page = (int) Math.min(requestedPage, Math.max(1, totalPages));
        var items = questions.selectPageRows(conditions(roundId, userId, query.keyword(), query.topic())
                        .orderByDesc(InterviewQuestion::getId), PAGE_SIZE, (page - 1L) * PAGE_SIZE)
                .stream().map(InterviewQuestionResponse::from).toList();
        return new InterviewQuestionPageResponse(items, page, PAGE_SIZE, total, totalPages,
                questions.selectTopicsForRound(roundId, userId));
    }

    private LambdaQueryWrapper<InterviewQuestion> conditions(Long roundId, Long userId, String keyword, String topic) {
        var conditions = new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewRoundId, roundId).eq(InterviewQuestion::getUserId, userId);
        if (topic != null) {
            // 完整主题相等，与关键词取交集；不把主题内容解释为 LIKE 通配符。
            conditions.apply("LOWER(knowledge_topic) = LOWER({0})", topic);
        }
        if (keyword != null) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            // 归属 AND (四字段 OR)，不能让 OR 绕过当前用户/轮次；固定 SQL 片段和值分离。
            conditions.and(search -> search
                    .apply("LOWER(question) LIKE LOWER({0}) ESCAPE '!'", pattern)
                    .or().apply("LOWER(answer_notes) LIKE LOWER({0}) ESCAPE '!'", pattern)
                    .or().apply("LOWER(knowledge_topic) LIKE LOWER({0}) ESCAPE '!'", pattern)
                    .or().apply("LOWER(review_notes) LIKE LOWER({0}) ESCAPE '!'", pattern));
        }
        return conditions;
    }
}
