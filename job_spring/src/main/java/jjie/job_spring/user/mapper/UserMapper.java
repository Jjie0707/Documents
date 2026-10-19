package jjie.job_spring.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jjie.job_spring.user.common.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserAccount> {
}
