package jjie.job_spring.job.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class JobUrlValidator implements ConstraintValidator<ValidJobUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        try {
            // 只解析语法，不访问链接；要求明确的服务端主机，不接受协议相对地址或用户信息。
            URI uri = new URI(value).parseServerAuthority();
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && !uri.getRawAuthority().endsWith(":")
                    && (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
