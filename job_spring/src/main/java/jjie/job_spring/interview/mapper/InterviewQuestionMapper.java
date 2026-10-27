package jjie.job_spring.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import java.util.List;
import jjie.job_spring.interview.common.InterviewQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
    @Select("SELECT * FROM interview_questions WHERE id = #{id} AND user_id = #{userId} FOR UPDATE")
    InterviewQuestion selectOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // Wrapper 结构仅由 Service 构造；关键词、条数与偏移量均绑定参数，沿用岗位分页写法。
    @Select("SELECT * FROM interview_questions ${ew.customSqlSegment} LIMIT #{size} OFFSET #{offset}")
    List<InterviewQuestion> selectPageRows(@Param(Constants.WRAPPER) Wrapper<InterviewQuestion> conditions,
                                         @Param("size") int size, @Param("offset") long offset);

    // 本人本轮的完整主题选项不受关键词和当前分页影响；同名大小写归为一项。
    @Select("""
            SELECT MIN(knowledge_topic) FROM interview_questions
            WHERE interview_round_id = #{roundId} AND user_id = #{userId}
                AND knowledge_topic IS NOT NULL AND TRIM(knowledge_topic) <> ''
            GROUP BY LOWER(knowledge_topic) ORDER BY LOWER(knowledge_topic)
            """)
    List<String> selectTopicsForRound(@Param("roundId") Long roundId, @Param("userId") Long userId);

    @Delete("DELETE FROM interview_questions WHERE interview_round_id = #{roundId} AND user_id = #{userId}")
    int deleteForRound(@Param("roundId") Long roundId, @Param("userId") Long userId);

    @Delete("""
            DELETE FROM interview_questions
            WHERE user_id = #{userId} AND interview_round_id IN (
                SELECT id FROM interview_rounds WHERE job_application_id = #{jobId} AND user_id = #{userId}
            )
            """)
    int deleteForJob(@Param("jobId") Long jobId, @Param("userId") Long userId);
}
