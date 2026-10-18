package jjie.job_spring.auth;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException() {
        super("用户名已存在");
    }
}
