package jjie.job_spring.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;

import jjie.job_spring.job.common.JobResponse;
import jjie.job_spring.job.common.JobApplication;
import jjie.job_spring.job.common.JobListQuery;
import jjie.job_spring.job.common.JobPageResponse;
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

    public JobPageResponse listForUser(Long currentUserId, JobListQuery query) {
        LambdaQueryWrapper<JobApplication> conditions = new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, currentUserId);
        if (query.status() != null) {
            conditions.eq(JobApplication::getStatus, query.status().name());
        }
        if (query.keyword() != null) {
            String pattern = containsPattern(query.keyword());
            // SQL 片段固定；{0} 由 MyBatis-Plus 绑定为参数，关键词不拼入 SQL。
            // AND (...) 必须将 OR 搜索分组，不能让其他用户的岗位绕过归属条件。
            conditions.and(search -> search
                    .apply("LOWER(company_name) LIKE LOWER({0}) ESCAPE '!'", pattern)
                    .or().apply("LOWER(job_title) LIKE LOWER({0}) ESCAPE '!'", pattern)
                    .or().apply("LOWER(city) LIKE LOWER({0}) ESCAPE '!'", pattern));
        }
        // COUNT 和取页共用筛选条件；COUNT 不需要排序，也不带 LIMIT。
        long total = mapper.selectCount(conditions);
        long totalPages = total / query.size() + (total % query.size() == 0 ? 0 : 1);
        // 请求页超过末页时返回末页；无结果时统一为第 1 页、共 0 页。
        int page = (int) Math.min(query.page(), Math.max(1, totalPages));
        long offset = (page - 1L) * query.size();
        List<JobResponse> items = total == 0 ? List.of() : mapper.selectPageRows(
                        conditions.orderByDesc(JobApplication::getId), query.size(), offset)
                .stream().map(JobResponse::from).toList();
        return new JobPageResponse(items, page, query.size(), total, totalPages);
    }

    private static String containsPattern(String keyword) {
        // ! 为显式 LIKE 转义符，先转义其自身，再转义用户输入的通配字符。
        return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
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
