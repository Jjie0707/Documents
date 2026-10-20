package jjie.job_spring.auth.common;

import jjie.job_spring.user.common.UserAccount;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public class AppUserDetails extends User {

    private final Long id;

    public AppUserDetails(UserAccount account) {
        super(account.getUsername(), account.getPasswordHash(),
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.id = account.getId();
    }

    public Long getId() {
        return id;
    }
}
