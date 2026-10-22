package jjie.job_spring.interview.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("interview_questions")
public class InterviewQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long interviewRoundId;
    private String question;
    private String answerNotes;
    private String knowledgeTopic;
    private String reviewNotes;
}
