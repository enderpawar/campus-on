package com.campuson.api.global.security;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
class ClientAddressResolverTest {
    private String resolve(String trusted,String peer,String xff) {
        var request=new MockHttpServletRequest();request.setRemoteAddr(peer);request.addHeader("X-Forwarded-For",xff);
        return new ClientAddressResolver(trusted).resolve(request);
    }
    @Test void untrustedPeerCannotSpoofForwardedAddress() { assertEquals("192.0.2.1",resolve("","192.0.2.1","203.0.113.2")); }
    @Test void trustedChainIsReadFromRightAndStopsAtFirstUntrustedHop() {
        assertEquals("203.0.113.2",resolve("10.0.0.0/8","10.1.1.1","198.51.100.9, 203.0.113.2, 10.2.2.2"));
    }
    @Test void brokenChainFallsBackToSocketIncludingEmptyAndHostNames() {
        for(String header:new String[]{"bad, 203.0.113.2","203.0.113.2,", "localhost", "999.1.1.1"})
            assertEquals("10.1.1.1",resolve("10.0.0.0/8","10.1.1.1",header));
    }
    @Test void repeatedForwardingHeadersAreOneChainReadFromRight() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("10.1.1.1");
        request.addHeader("X-Forwarded-For","198.51.100.9");request.addHeader("X-Forwarded-For","203.0.113.2");
        assertEquals("203.0.113.2",new ClientAddressResolver("10.0.0.0/8").resolve(request));
    }
    @Test void ipv6CidrSupportsTrustedProxyAndClient() {
        assertEquals("2001:db8:2::1",resolve("2001:db8:1::/48","2001:db8:1::1","2001:db8:2::1"));
    }
}
