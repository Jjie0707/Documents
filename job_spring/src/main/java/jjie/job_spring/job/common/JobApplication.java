package jjie.job_spring.job.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("job_applications")
public class JobApplication {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String companyName;
    private String jobTitle;
    private String city;
    private String sourcePlatform;
    private String originalUrl;
    private String salary;
    private String responsibilities;
    private String requirements;
    private String educationRequirement;
    private LocalDate applicationDeadline;
    private String status;
}
