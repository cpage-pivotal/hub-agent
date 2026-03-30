package org.tanzu.hubmcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tanzu.goose.cf.broker.CredentialBrokerClient;
import org.tanzu.goose.cf.broker.InstanceIdentitySSLContextFactory;
import org.tanzu.hubmcp.config.TanzuPlatformProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fetches Tanzu Hub access tokens from the Agent Credential Broker.
 * <p>
 * Delegates mTLS setup and broker communication to the shared
 * {@link CredentialBrokerClient} from goose-cf-wrapper. Adds
 * in-memory token caching with proactive refresh before expiry.
 * <p>
 * Falls back to a static token from config for local development when
 * no broker URL is configured.
 */
@Service
public class CredentialBrokerService {

    private static final Logger log = LoggerFactory.getLogger(CredentialBrokerService.class);
    private static final Duration REFRESH_BUFFER = Duration.ofMinutes(2);
    private static final String TARGET_SYSTEM = "tanzu-hub";

    private final TanzuPlatformProperties properties;
    private final CredentialBrokerClient brokerClient;

    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt;

    public CredentialBrokerService(TanzuPlatformProperties properties) {
        this.properties = properties;
        this.brokerClient = isBrokerConfigured() ? createBrokerClient() : null;
    }

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

            if (brokerClient != null) {
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

    private void fetchTokenFromBroker() {
        var response = brokerClient.requestAccess(TARGET_SYSTEM, null);

        if (response instanceof CredentialBrokerClient.UserDelegationRequired) {
            throw new IllegalStateException(
                    "Broker requires user to grant access to '" + TARGET_SYSTEM
                    + "'. Visit the broker UI to authorize.");
        }

        if (response instanceof CredentialBrokerClient.ResourceAccessToken token) {
            cachedToken = token.token();
            tokenExpiresAt = token.expiresAt() != null
                    ? token.expiresAt()
                    : Instant.now().plus(Duration.ofMinutes(25));
            log.info("Obtained token from broker for '{}', expires at {}", TARGET_SYSTEM, tokenExpiresAt);
        }
    }

    private boolean isBrokerConfigured() {
        var broker = properties.broker();
        return broker != null
                && broker.url() != null && !broker.url().isBlank()
                && broker.delegationToken() != null && !broker.delegationToken().isBlank();
    }

    private CredentialBrokerClient createBrokerClient() {
        var broker = properties.broker();
        boolean isInternalRoute = broker.url().contains(".apps.internal");
        var sslContext = InstanceIdentitySSLContextFactory.createIfAvailable(isInternalRoute);

        if (sslContext != null) {
            log.info("CF instance identity detected — mTLS enabled for broker at {}", broker.url());
            return new CredentialBrokerClient(broker.url(), broker.delegationToken(),
                    () -> InstanceIdentitySSLContextFactory.createIfAvailable(isInternalRoute));
        }

        log.info("No CF instance identity — broker client without mTLS for {}", broker.url());
        return new CredentialBrokerClient(broker.url(), broker.delegationToken());
    }
}
