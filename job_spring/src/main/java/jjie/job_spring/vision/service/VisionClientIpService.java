package jjie.job_spring.vision.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import jjie.job_spring.config.VisionQuotaProperties;
import org.springframework.stereotype.Service;

@Service
public class VisionClientIpService {
    private final VisionQuotaProperties properties;
    public VisionClientIpService(VisionQuotaProperties properties) { this.properties = properties; }

    public String resolve(HttpServletRequest request) {
        String peer = normalize(request.getRemoteAddr());
        if (peer == null) return null;
        try {
            if (!trusted(peer)) return peer; // 不可信连接的任何转发头均忽略。
            var headers = Collections.list(request.getHeaders("X-Forwarded-For"));
            if (headers.size() != 1 || headers.get(0).length() > 1024) return null;
            String[] chain = headers.get(0).split(",", -1);
            if (chain.length > 16) return null;
            // 先验证整条链，禁止空值、端口、主机名或损坏的前缀。
            String[] addresses = Arrays.stream(chain).map(String::strip).map(VisionClientIpService::normalize)
                    .toArray(String[]::new);
            if (Arrays.stream(addresses).anyMatch(java.util.Objects::isNull)) return null;
            for (int i = addresses.length - 1; i >= 0; i--) {
                if (!trusted(addresses[i])) return addresses[i];
            }
            return null; // 全部为代理，无法建立客户端身份，不能任取伪造的最左值。
        } catch (IllegalArgumentException exception) {
            return null; // 可信代理配置错误时停止识别，手动功能仍可用。
        }
    }

    private boolean trusted(String address) {
        if (properties.getTrustedProxies() == null) throw new IllegalArgumentException();
        boolean match = false;
        for (String cidr : properties.getTrustedProxies()) {
            if (cidr == null) throw new IllegalArgumentException();
            String[] parts = cidr.split("/", -1);
            String network = normalize(parts[0]);
            if (parts.length > 2 || network == null) throw new IllegalArgumentException();
            byte[] net = literal(network).getAddress(), candidate = literal(address).getAddress();
            int bits;
            try { bits = parts.length == 1 ? net.length * 8 : Integer.parseInt(parts[1]); }
            catch (NumberFormatException exception) { throw new IllegalArgumentException(); }
            if (bits < 1 || bits > net.length * 8) throw new IllegalArgumentException(); // 禁止 /0 全网信任。
            if (net.length != candidate.length) continue;
            boolean same = true;
            for (int i = 0; i < bits; i++) {
                int mask = 1 << (7 - i % 8);
                if ((net[i / 8] & mask) != (candidate[i / 8] & mask)) { same = false; break; }
            }
            match |= same;
        }
        return match;
    }

    public static String normalize(String address) {
        if (address == null || address.length() > 45) return null;
        // 只允许数字地址；不接受 DNS、zone id、括号或附带端口。先检查再调用 JDK，避免 DNS 查询。
        if (address.contains(":")) {
            if (!address.matches("[0-9a-fA-F:.]+")) return null;
        } else {
            String[] parts = address.split("\\.", -1);
            if (parts.length != 4) return null;
            for (String part : parts) {
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return null;
            }
        }
        try { return InetAddress.getByName(address).getHostAddress(); }
        catch (java.net.UnknownHostException exception) { return null; }
    }

    private static InetAddress literal(String normalized) {
        try { return InetAddress.getByName(normalized); }
        catch (java.net.UnknownHostException exception) { throw new IllegalArgumentException(); }
    }
}
