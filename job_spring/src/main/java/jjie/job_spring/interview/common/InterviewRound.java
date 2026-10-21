package jjie.job_spring.interview.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("interview_rounds")
public class InterviewRound {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long jobApplicationId;
    private Integer roundNo;
    private LocalDateTime interviewTime;
    private String roundType;
    private String result;
    private String review;
}
