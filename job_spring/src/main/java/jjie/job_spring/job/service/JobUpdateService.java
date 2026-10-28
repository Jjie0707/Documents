package jjie.job_spring.job.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jjie.job_spring.job.common.JobApplication;
import jjie.job_spring.job.common.JobResponse;
import jjie.job_spring.job.common.UpdateJobRequest;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobUpdateService {
    private final JobMapper mapper;
    private final JobReadService readService;

    public JobUpdateService(JobMapper mapper, JobReadService readService) {
        this.mapper = mapper;
        this.readService = readService;
    }

    @Transactional
    public JobResponse updateForUser(Long id, Long currentUserId, UpdateJobRequest request) {
        mapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, id)
                .eq(JobApplication::getUserId, currentUserId)
                .set(JobApplication::getCompanyName, request.companyName())
                .set(JobApplication::getJobTitle, request.jobTitle())
                // 显式 SET 允许将选填项写为 SQL NULL，而不是跳过空值。
                .set(JobApplication::getCity, request.city())
                .set(JobApplication::getSalary, request.salary())
                .set(JobApplication::getResponsibilities, request.responsibilities())
                .set(JobApplication::getRequirements, request.requirements()));
        // 相同内容也允许成功；按归属回读判断记录是否存在，不用影响行数判断 404。
        return readService.findForUser(id, currentUserId);
    }
}
