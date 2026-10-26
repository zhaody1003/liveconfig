package com.z.liveconfig.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;

/**
 * 实例身份探测。
 *
 * 内网 IP 本地必得（遍历网卡取 site-local）且在 VPC 内唯一，所以它是「按服务器设值」的主键；
 * 公网 IP 机器自己不知道（在 NAT/VPC 后面），要么问回显服务要么问云 metadata，
 * 而且多机可能共享同一个公网 IP —— 因此公网 IP 只用于展示，绝不参与主键。
 */
final class InstanceIdentity {

    private static final Logger log = LoggerFactory.getLogger(InstanceIdentity.class);

    private InstanceIdentity() {
    }

    static FlagContext.InstanceInfo detect(String appName, String publicIpEchoUrl) {
        return new FlagContext.InstanceInfo(localPrivateIp(), fetchPublicIp(publicIpEchoUrl), appName);
    }

    static String localPrivateIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("内网 IP 探测失败，服务器定向将不可用：{}", e.getMessage());
        }
        return null;
    }

    /** 拿不到就留空，只影响控制台展示，不影响任何判定 */
    private static String fetchPublicIp(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(1)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return response.body().trim();
        } catch (Exception e) {
            log.debug("公网 IP 获取失败（忽略）：{}", e.getMessage());
            return null;
        }
    }
}
