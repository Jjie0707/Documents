package jjie.job_spring.job.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jjie.job_spring.job.common.JobApplication;
import jjie.job_spring.job.common.JobResponse;
import jjie.job_spring.job.common.JobStatus;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobStatusService {
    private final JobMapper mapper;
    private final JobReadService readService;

    public JobStatusService(JobMapper mapper, JobReadService readService) {
        this.mapper = mapper;
        this.readService = readService;
    }

    @Transactional
    public JobResponse updateForUser(Long id, Long currentUserId, JobStatus status) {
        mapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, id)
                .eq(JobApplication::getUserId, currentUserId)
                .set(JobApplication::getStatus, status.name()));
        // 重复设置相同状态可能影响 0 行，不能仅据此判定不存在。
        // 仍按当前用户回读；不存在与属于其他用户都由 readService 返回 404。
        return readService.findForUser(id, currentUserId);
    }
}
