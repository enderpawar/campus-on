package com.campuson.api.global.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientAddressResolver {
    private final List<Network> trusted;
    public ClientAddressResolver(@Value("${campus.security.trusted-proxies:}") String cidrs) {
        trusted = cidrs.isBlank() ? List.of() : Arrays.stream(cidrs.split(",")).map(String::strip).map(Network::parse).toList();
    }
    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        byte[] direct = literal(peer);
        if (direct == null || !trusted(direct)) return peer;
        String xff = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
        if (xff.isEmpty() || xff.length() > 4096) return peer;
        String[] chain = xff.split(",", -1);
        List<byte[]> parsed = new ArrayList<>();
        for (String hop : chain) {
            byte[] address = literal(hop.strip());
            if (address == null) return peer;
            parsed.add(address);
        }
        for (int i = parsed.size() - 1; i >= 0; i--) if (!trusted(parsed.get(i))) return chain[i].strip();
        return chain[0].strip();
    }
    private boolean trusted(byte[] address) { return trusted.stream().anyMatch(n -> n.contains(address)); }
    // Numeric literals only: never use DNS to interpret an untrusted header.
    private static byte[] literal(String value) {
        if (value == null || value.isEmpty() || value.contains("%") || !value.matches("[0-9a-fA-F:.]+")) return null;
        if (!value.contains(":")) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return null;
            byte[] result = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (!parts[i].matches("0|[1-9][0-9]{0,2}")) return null;
                int n = Integer.parseInt(parts[i]); if (n > 255) return null; result[i] = (byte)n;
            }
            return result;
        }
        try { return InetAddress.getByName(value).getAddress(); } catch (Exception ex) { return null; }
    }
    private record Network(byte[] address, int bits) {
        static Network parse(String cidr) {
            String[] parts = cidr.split("/", -1); byte[] address = literal(parts[0]);
            if (address == null || parts.length > 2) throw new IllegalArgumentException("Invalid trusted proxy CIDR");
            int bits = parts.length == 1 ? address.length * 8 : Integer.parseInt(parts[1]);
            if (bits < 0 || bits > address.length * 8) throw new IllegalArgumentException("Invalid trusted proxy prefix");
            return new Network(address, bits);
        }
        boolean contains(byte[] other) {
            if (address.length != other.length) return false;
            for (int i = 0; i < bits; i++) if (((address[i/8] ^ other[i/8]) & (128 >> (i%8))) != 0) return false;
            return true;
        }
    }
}
