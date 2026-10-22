package jjie.job_spring.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import java.util.List;
import jjie.job_spring.job.common.JobApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobMapper extends BaseMapper<JobApplication> {
    // 面试新增/编辑/删除及岗位删除统一先锁所属岗位，协调同岗位的并发写入。
    @Select("SELECT id FROM job_applications WHERE id = #{jobId} AND user_id = #{userId} FOR UPDATE")
    Long lockOwnedJob(@Param("jobId") Long jobId, @Param("userId") Long userId);

    // SQL 结构只来自 Service 构造的 Wrapper；筛选值、条数和偏移量都通过参数绑定。
    @Select("SELECT * FROM job_applications ${ew.customSqlSegment} LIMIT #{limit} OFFSET #{offset}")
    List<JobApplication> selectPageRows(@Param(Constants.WRAPPER) Wrapper<JobApplication> conditions,
                                        @Param("limit") int limit, @Param("offset") long offset);
}
