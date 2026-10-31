package jjie.job_spring.vision;

import java.util.List;
import jjie.job_spring.config.VisionQuotaProperties;
import jjie.job_spring.vision.service.VisionClientIpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

class VisionClientIpServiceTests {
    private final VisionQuotaProperties properties = new VisionQuotaProperties();
    private final VisionClientIpService service = new VisionClientIpService(properties);

    private MockHttpServletRequest request(String peer, String header) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (header != null) request.addHeader("X-Forwarded-For", header);
        request.addHeader("CF-Connecting-IP", "192.0.2.99");
        request.addHeader("Forwarded", "for=192.0.2.99");
        return request;
    }

    @Test void untrustedPeerCannotForgeAnyForwardingHeaders() {
        assertThat(service.resolve(request("192.0.2.1", "192.0.2.99"))).isEqualTo("192.0.2.1");
    }

    @Test void walksFromRightAndStopsAtFirstUntrustedHop() {
        properties.setTrustedProxies(List.of("10.0.0.0/24", "2001:db8:1::/64"));
        assertThat(service.resolve(request("10.0.0.2", "192.0.2.99, 198.51.100.5, 2001:db8:1::1")))
                .isEqualTo("198.51.100.5");
        assertThat(service.resolve(request("10.0.1.2", "192.0.2.99"))).isEqualTo("10.0.1.2");
    }

    @Test void equivalentIpv6AndMappedIpv4ShareIdentity() {
        assertThat(VisionClientIpService.normalize("2001:db8::1"))
                .isEqualTo(VisionClientIpService.normalize("2001:0DB8:0:0:0:0:0:1"));
        assertThat(VisionClientIpService.normalize("::ffff:192.0.2.1")).isEqualTo("192.0.2.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost", "192.0.2.1:80", "[::1]", "fe80::1%en0", "192.000.2.1", "256.2.3.4", "1.2.3", "unknown"})
    void invalidAddressesCannotCreateSeparateQuotaIdentity(String address) {
        assertThat(VisionClientIpService.normalize(address)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "192.0.2.1,", "unknown", "192.0.2.1, bad", "10.0.0.1"})
    void trustedPeerWithMalformedOrAllTrustedChainFailsClosed(String header) {
        properties.setTrustedProxies(List.of("10.0.0.0/24"));
        assertThat(service.resolve(request("10.0.0.2", header))).isNull();
    }

    @Test void missingDuplicateAndOversizedForwardingHeadersFailClosed() {
        properties.setTrustedProxies(List.of("10.0.0.2"));
        assertThat(service.resolve(request("10.0.0.2", null))).isNull();
        var duplicate = request("10.0.0.2", "192.0.2.1");
        duplicate.addHeader("X-Forwarded-For", "192.0.2.2");
        assertThat(service.resolve(duplicate)).isNull();
        assertThat(service.resolve(request("10.0.0.2", "x".repeat(1025)))).isNull();
        assertThat(service.resolve(request("10.0.0.2", "192.0.2.1,".repeat(17) + "192.0.2.1"))).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0/0", "::/0", "10.0.0.0/33", "localhost/32", "10.0.0.0/", "10.0.0.0/24/1"})
    void invalidTrustConfigurationFailsClosed(String cidr) {
        properties.setTrustedProxies(List.of(cidr));
        assertThat(service.resolve(request("10.0.0.2", "192.0.2.1"))).isNull();
    }
}
