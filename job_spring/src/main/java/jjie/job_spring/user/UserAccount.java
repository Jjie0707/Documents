package jjie.job_spring.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("users")
public class UserAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @TableField("password_hash")
    private String passwordHash;
}
