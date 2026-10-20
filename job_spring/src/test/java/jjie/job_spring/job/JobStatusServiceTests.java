package jjie.job_spring.job;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import jjie.job_spring.job.common.JobApplication;
import jjie.job_spring.job.common.JobStatus;
import jjie.job_spring.job.mapper.JobMapper;
import jjie.job_spring.job.service.JobReadService;
import jjie.job_spring.job.service.JobStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobStatusServiceTests {
    @Test
    void zeroAffectedRowsStillSucceedsForExistingOwnedJob() {
        JobMapper mapper = mock(JobMapper.class);
        JobApplication existing = new JobApplication();
        existing.setId(1L);
        existing.setUserId(2L);
        existing.setStatus("APPLIED");
        // 模拟重复更新相同值且驱动返回“实际改变行数”的情形。
        when(mapper.update(isNull(), ArgumentMatchers.<Wrapper<JobApplication>>any())).thenReturn(0);
        when(mapper.selectOne(any())).thenReturn(existing);
        JobStatusService service = new JobStatusService(mapper, new JobReadService(mapper));
        assertThat(service.updateForUser(1L, 2L, JobStatus.APPLIED).status()).isEqualTo("APPLIED");
    }
}
