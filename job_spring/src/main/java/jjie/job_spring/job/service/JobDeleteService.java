package jjie.job_spring.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jjie.job_spring.job.common.JobApplication;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobDeleteService {
    private final JobMapper mapper;

    public JobDeleteService(JobMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void deleteForUser(Long id, Long currentUserId) {
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
