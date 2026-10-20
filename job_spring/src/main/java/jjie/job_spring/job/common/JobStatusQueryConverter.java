package jjie.job_spring.job.common;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

// URL 查询参数走 MVC 类型转换；JSON 的 @JsonCreator 不处理这里的输入。
@Component
public class JobStatusQueryConverter implements Converter<String, JobStatus> {
    @Override
    public JobStatus convert(String source) {
        String value = source.strip();
        // 空值表示全部状态；非空值只接受枚举代码，不接受数字或猜测大小写。
        return value.isEmpty() ? null : JobStatus.valueOf(value);
    }
}
