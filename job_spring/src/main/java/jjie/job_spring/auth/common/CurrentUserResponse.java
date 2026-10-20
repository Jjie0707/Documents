package jjie.job_spring.auth.common;

public record CurrentUserResponse(Long id, String username) {
    public static CurrentUserResponse from(AppUserDetails user) {
        return new CurrentUserResponse(user.getId(), user.getUsername());
    }
}
