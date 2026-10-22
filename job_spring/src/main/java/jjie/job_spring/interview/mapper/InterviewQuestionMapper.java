package jjie.job_spring.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import jjie.job_spring.interview.common.InterviewQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
    @Select("""
            SELECT * FROM interview_questions
            WHERE interview_round_id = #{roundId} AND user_id = #{userId}
            ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<InterviewQuestion> selectPageRows(@Param("roundId") Long roundId, @Param("userId") Long userId,
                                         @Param("size") int size, @Param("offset") long offset);

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
