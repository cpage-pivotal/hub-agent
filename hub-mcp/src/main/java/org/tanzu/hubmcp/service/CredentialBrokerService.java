package org.tanzu.hubmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tanzu.hubmcp.config.TanzuPlatformProperties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fetches Tanzu Hub access tokens from the Agent Credential Broker.
 * <p>
 * On Cloud Foundry, uses the CF instance identity certificate for mTLS
 * authentication against the broker. Caches the token in memory and
 * proactively refreshes before expiry.
 * <p>
 * Falls back to a static token from config for local development when
 * no broker URL is configured.
 */
@Service
public class CredentialBrokerService {

    private static final Logger log = LoggerFactory.getLogger(CredentialBrokerService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REFRESH_BUFFER = Duration.ofMinutes(2);
    private static final String TARGET_SYSTEM = "tanzu-hub";

    private final TanzuPlatformProperties properties;
    private final ObjectMapper objectMapper;

    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt;

    public CredentialBrokerService(TanzuPlatformProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a valid Tanzu Hub access token, fetching from the broker if needed.
     */
    public String getToken() {
        if (cachedToken != null && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt.minus(REFRESH_BUFFER))) {
            return cachedToken;
        }

        tokenLock.lock();
        try {
            if (cachedToken != null && tokenExpiresAt != null
                    && Instant.now().isBefore(tokenExpiresAt.minus(REFRESH_BUFFER))) {
                return cachedToken;
            }

            if (isBrokerConfigured()) {
                fetchTokenFromBroker();
            } else if (properties.fallbackToken() != null && !properties.fallbackToken().isBlank()) {
                log.debug("Using fallback static token (no broker configured)");
                cachedToken = properties.fallbackToken();
                tokenExpiresAt = Instant.now().plus(Duration.ofMinutes(25));
            } else {
                throw new IllegalStateException(
                        "No credential broker configured and no fallback token set. "
                        + "Set BROKER_URL + BROKER_DELEGATION_TOKEN, or set TANZU_PLATFORM_FALLBACK_TOKEN for local dev.");
            }

            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean isBrokerConfigured() {
        var broker = properties.broker();
        return broker != null
                && broker.url() != null && !broker.url().isBlank()
                && broker.delegationToken() != null && !broker.delegationToken().isBlank();
    }

    @SuppressWarnings("unchecked")
    private void fetchTokenFromBroker() {
        try {
            var broker = properties.broker();
            var client = buildMtlsClient();

            var requestBody = objectMapper.writeValueAsString(
                    Map.of("targetSystem", TARGET_SYSTEM));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(broker.url() + "/api/credentials/request"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + broker.delegationToken())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Broker returned HTTP " + response.statusCode() + ": " + response.body());
            }

            var body = objectMapper.readValue(response.body(), Map.class);
            var type = (String) body.get("type");

            if ("user_delegation_required".equals(type)) {
                throw new IllegalStateException(
                        "Broker requires user to grant access to '" + TARGET_SYSTEM
                        + "'. Visit the broker UI to authorize.");
            }

            cachedToken = (String) body.get("token");
            var expiresAtStr = (String) body.get("expiresAt");
            if (expiresAtStr != null) {
                tokenExpiresAt = Instant.parse(expiresAtStr);
            } else {
                tokenExpiresAt = Instant.now().plus(Duration.ofMinutes(25));
            }

            log.info("Obtained token from broker for '{}', expires at {}", TARGET_SYSTEM, tokenExpiresAt);

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch token from credential broker: " + e.getMessage(), e);
        }
    }

    private HttpClient buildMtlsClient() throws Exception {
        var certPath = System.getenv("CF_INSTANCE_CERT");
        var keyPath = System.getenv("CF_INSTANCE_KEY");

        if (certPath != null && keyPath != null) {
            // CF Instance Identity certs have UUID-based SANs, not the apps.internal
            // hostname. Disable hostname verification for this mTLS connection —
            // security is provided by mutual certificate validation, not hostnames.
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

            var sslContext = buildSslContext(Path.of(certPath), Path.of(keyPath));
            return HttpClient.newBuilder()
                    .connectTimeout(REQUEST_TIMEOUT)
                    .sslContext(sslContext)
                    .build();
        }

        return HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    private SSLContext buildSslContext(Path certFile, Path keyFile) throws Exception {
        var certPem = Files.readString(certFile, StandardCharsets.UTF_8);
        var keyPem = Files.readString(keyFile, StandardCharsets.UTF_8);

        var certFactory = CertificateFactory.getInstance("X.509");
        var certs = certFactory.generateCertificates(
                new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8)));

        var privateKey = readPrivateKey(keyPem);

        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client",
                privateKey,
                new char[0],
                certs.toArray(new X509Certificate[0]));

        var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        // Trust all server certificates for the broker connection.
        // Security is provided by mTLS — the broker validates our client cert
        // to verify workload identity. The broker runs on CF internal networking
        // where the server cert SANs don't match the apps.internal hostname.
        var permissiveTm = new javax.net.ssl.X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };

        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), new javax.net.ssl.TrustManager[]{permissiveTm}, null);
        return sslContext;
    }

    private java.security.PrivateKey readPrivateKey(String pem) throws Exception {
        if (pem.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            var der = Base64.getDecoder().decode(extractBase64(pem, "RSA PRIVATE KEY"));
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1InPkcs8(der)));
        } else if (pem.contains("-----BEGIN EC PRIVATE KEY-----")) {
            var der = Base64.getDecoder().decode(extractBase64(pem, "EC PRIVATE KEY"));
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(wrapEcInPkcs8(der)));
        } else {
            var der = Base64.getDecoder().decode(extractBase64(pem, "PRIVATE KEY"));
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (Exception e) {
                return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            }
        }
    }

    private static String extractBase64(String pem, String label) {
        var begin = "-----BEGIN " + label + "-----";
        var end = "-----END " + label + "-----";
        int s = pem.indexOf(begin) + begin.length();
        int e = pem.indexOf(end);
        return pem.substring(s, e).replaceAll("\\s", "");
    }

    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1Key) {
        byte[] rsaOid = {
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        return buildPkcs8(rsaOid, pkcs1Key);
    }

    private static byte[] wrapEcInPkcs8(byte[] ecKey) {
        byte[] ecOid = {
                0x30, 0x13,
                0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01,
                0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x03, 0x01, 0x07
        };
        return buildPkcs8(ecOid, ecKey);
    }

    private static byte[] buildPkcs8(byte[] algorithmId, byte[] privateKey) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] keyOctet = wrapInTag((byte) 0x04, privateKey);
        byte[] inner = concat(version, algorithmId, keyOctet);
        return wrapInTag((byte) 0x30, inner);
    }

    private static byte[] wrapInTag(byte tag, byte[] content) {
        byte[] len = derLength(content.length);
        byte[] result = new byte[1 + len.length + content.length];
        result[0] = tag;
        System.arraycopy(len, 0, result, 1, len.length);
        System.arraycopy(content, 0, result, 1 + len.length, content.length);
        return result;
    }

    private static byte[] derLength(int length) {
        if (length < 128) return new byte[]{(byte) length};
        if (length < 256) return new byte[]{(byte) 0x81, (byte) length};
        return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) (length & 0xff)};
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
