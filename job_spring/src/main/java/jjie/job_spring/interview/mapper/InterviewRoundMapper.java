package jjie.job_spring.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jjie.job_spring.interview.common.InterviewRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InterviewRoundMapper extends BaseMapper<InterviewRound> {
    @Select("""
            SELECT * FROM interview_rounds
            WHERE job_application_id = #{jobId} AND user_id = #{userId}
            ORDER BY round_no ASC, id ASC LIMIT 1 OFFSET #{offset}
            """)
    InterviewRound selectPageRow(@Param("jobId") Long jobId, @Param("userId") Long userId,
                                 @Param("offset") long offset);

    @Select("""
            SELECT COUNT(*) FROM interview_rounds
            WHERE job_application_id = #{jobId} AND user_id = #{userId}
              AND (round_no < #{roundNo} OR (round_no = #{roundNo} AND id < #{roundId}))
            """)
    long countBeforeRound(@Param("jobId") Long jobId, @Param("userId") Long userId,
                          @Param("roundNo") Integer roundNo, @Param("roundId") Long roundId);

    @Select("SELECT * FROM interview_rounds WHERE id = #{id} AND user_id = #{userId} FOR UPDATE")
    InterviewRound selectOwnedRoundForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // FOR UPDATE 使用当前读，避免事务中已有快照时漏掉前一个请求刚提交的轮次。
    @Select("""
            SELECT id FROM interview_rounds
            WHERE job_application_id = #{jobId} AND user_id = #{userId} AND round_no = #{roundNo}
            ORDER BY id LIMIT 1 FOR UPDATE
            """)
    Long findExistingRoundId(@Param("jobId") Long jobId, @Param("userId") Long userId,
                            @Param("roundNo") Integer roundNo);
}
