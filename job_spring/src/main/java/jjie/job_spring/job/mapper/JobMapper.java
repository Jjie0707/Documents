package jjie.job_spring.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jjie.job_spring.job.common.JobApplication;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobMapper extends BaseMapper<JobApplication> {
}
