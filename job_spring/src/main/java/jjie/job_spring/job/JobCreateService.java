package jjie.job_spring.job;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobCreateService {
    private final JobMapper mapper;

    public JobCreateService(JobMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public JobResponse createForUser(CreateJobRequest request, Long currentUserId) {
        JobApplication job = new JobApplication();
        job.setUserId(currentUserId);
        job.setCompanyName(request.companyName());
        job.setJobTitle(request.jobTitle());
        job.setCity(request.city());
        job.setSalary(request.salary());
        job.setStatus("TO_APPLY");
        // AUTO 主键由数据库生成，MyBatis-Plus 将生成的 ID 回填到 job。
        mapper.insert(job);
        return JobResponse.from(job);
    }
}
