package jjie.job_spring.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jjie.job_spring.user.common.UserAccount;
import jjie.job_spring.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username()))) {
            throw new UsernameAlreadyExistsException();
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 两个请求可能同时通过查重，最终以数据库唯一索引为准。
            throw new UsernameAlreadyExistsException();
        }
        return new RegistrationResponse(user.getId(), user.getUsername());
    }
}
