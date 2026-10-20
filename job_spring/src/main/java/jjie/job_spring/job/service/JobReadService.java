package jjie.job_spring.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

import jjie.job_spring.job.common.JobResponse;
import jjie.job_spring.job.common.JobApplication;
import jjie.job_spring.job.mapper.JobMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobReadService {
    private final JobMapper mapper;

    public JobReadService(JobMapper mapper) {
        this.mapper = mapper;
    }

    public List<JobResponse> listForUser(Long currentUserId) {
        return mapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, currentUserId)
                        .orderByDesc(JobApplication::getId))
                .stream().map(JobResponse::from).toList();
    }

    public JobResponse findForUser(Long id, Long currentUserId) {
        JobApplication job = mapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, id)
                .eq(JobApplication::getUserId, currentUserId));
        // 不区分“不存在”和“属于其他用户”，避免泄露他人的记录是否存在。
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "岗位不存在");
        }
        return JobResponse.from(job);
    }
}
