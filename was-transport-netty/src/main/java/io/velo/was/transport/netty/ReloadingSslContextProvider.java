package io.velo.was.transport.netty;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.config.TlsMode;

import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ReloadingSslContextProvider {

    private final ServerConfiguration.Tls tls;
    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public ReloadingSslContextProvider(ServerConfiguration.Tls tls) {
        this.tls = tls;
    }

    public SslContext current() throws Exception {
        Snapshot snapshot = current.get();
        if (snapshot == null || snapshot.shouldReload(tls)) {
            Snapshot reloaded = buildSnapshot(tls);
            current.set(reloaded);
            return reloaded.sslContext();
        }
        return snapshot.sslContext();
    }

    private Snapshot buildSnapshot(ServerConfiguration.Tls tlsConfiguration) throws Exception {
        SslContextBuilder builder;
        List<String> protocols = tlsConfiguration.getProtocols();

        if (tlsConfiguration.getMode() == TlsMode.PKCS12) {
            KeyStore keyStore = KeyStore.getInstance(tlsConfiguration.getKeyStoreType());
            try (FileInputStream inputStream = new FileInputStream(tlsConfiguration.getKeyStoreFile())) {
                keyStore.load(inputStream, tlsConfiguration.getKeyStorePassword().toCharArray());
            }

            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, tlsConfiguration.getKeyStorePassword().toCharArray());
            builder = SslContextBuilder.forServer(keyManagerFactory);
        } else {
            builder = SslContextBuilder.forServer(
                    new File(tlsConfiguration.getCertChainFile()),
                    new File(tlsConfiguration.getPrivateKeyFile()),
                    emptyToNull(tlsConfiguration.getPrivateKeyPassword()));
        }

        if (protocols != null && !protocols.isEmpty()) {
            builder.protocols(protocols.toArray(String[]::new));
        }

        // Enable ALPN for HTTP/2 negotiation
        builder.applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1));

        // Prefer OpenSSL if available (better ALPN support), fall back to JDK
        builder.sslProvider(SslProvider.isAlpnSupported(SslProvider.OPENSSL)
                ? SslProvider.OPENSSL : SslProvider.JDK);

        return new Snapshot(builder.build(), resolveMarker(tlsConfiguration), Instant.now().toEpochMilli());
    }

    private Path resolveMarker(ServerConfiguration.Tls tlsConfiguration) {
        if (tlsConfiguration.getMode() == TlsMode.PKCS12) {
            return Path.of(tlsConfiguration.getKeyStoreFile());
        }
        return Path.of(tlsConfiguration.getCertChainFile());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Snapshot(SslContext sslContext, Path markerFile, long loadedAtEpochMillis) {
        private boolean shouldReload(ServerConfiguration.Tls tlsConfiguration) throws Exception {
            long intervalMillis = Math.max(1, tlsConfiguration.getReloadIntervalSeconds()) * 1000L;
            if (System.currentTimeMillis() - loadedAtEpochMillis < intervalMillis) {
                return false;
            }
            long fileTimestamp = Files.getLastModifiedTime(markerFile).toMillis();
            return fileTimestamp > loadedAtEpochMillis;
        }
    }
}
