package link.locutus.discord.web.jooby.handler;

import link.locutus.discord.config.Settings;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import jakarta.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocutusSSLHandler {
    public static Server configureServer(Server server, int portMain, int portHttps) {
        Set<Connector> connectors = new HashSet<>();
        if (portMain > 0) {
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(portMain);
            connectors.add(connector);
        }
        if (portHttps > 0) {
            ServerConnector sslConnector = new ServerConnector(server, createSslContextFactory());
            sslConnector.setPort(portHttps);
            connectors.add(sslConnector);
        }
        server.setConnectors(connectors.toArray(new Connector[0]));
        return server;
    }

    private static SslContextFactory createSslContextFactory() {
        String pathTo = Settings.INSTANCE.WEB.CERT_PATH;
        if (pathTo == null || pathTo.isEmpty()) {
            return new SslContextFactory.Server();
        }
        String keyPassword = Settings.INSTANCE.WEB.CERT_PASSWORD;
        try {
            byte[] keyBytes = parseDERFromPEM(Files.readAllBytes(new File(pathTo + File.separator + "privkey.pem").toPath().toRealPath()), "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----").get(0);
            List<byte[]> fullChain = parseDERFromPEM(Files.readAllBytes(new File(pathTo + File.separator + "fullchain.pem").toPath().toRealPath()), "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");

            List<X509Certificate> certificates = new ArrayList<>();
            for (byte[] certBytes : fullChain) {
                X509Certificate cert = generateCertificateFromDER(certBytes);
                certificates.add(cert);
            }


            RSAPrivateKey key = generatePrivateKeyFromDER(keyBytes);

            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(null);

            for (int i = 0; i < certificates.size(); i++) {
                X509Certificate cert = certificates.get(i);
                keystore.setCertificateEntry("cert-alias-" + i, cert);
            }


            if (keyPassword != null && !keyPassword.isEmpty()) {
                keystore.setKeyEntry("key-alias", key, keyPassword.toCharArray(), certificates.toArray(new X509Certificate[0]));
            } else {
                keystore.setKeyEntry("key-alias", key, null, certificates.toArray(new X509Certificate[0]));
            }

            SslContextFactory sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStore(keystore);
            if (keyPassword != null) {
                sslContextFactory.setKeyStorePassword(keyPassword);
            }

            return sslContextFactory;
        } catch (IOException | KeyStoreException | InvalidKeySpecException | NoSuchAlgorithmException | CertificateException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static List<byte[]> parseDERFromPEM(byte[] pem, String beginDelimiter, String endDelimiter) {
        String data = new String(pem, StandardCharsets.ISO_8859_1);
        String[] tokens = data.split(beginDelimiter);

        List<byte[]> binaryKeys = new ArrayList<>();
        for (String token : tokens) {
            if (!token.contains(endDelimiter)) continue;
            token = token.split(endDelimiter)[0];
            binaryKeys.add(DatatypeConverter.parseBase64Binary(token));
        }
        return binaryKeys;
    }

    private static RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes) throws InvalidKeySpecException, NoSuchAlgorithmException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) factory.generatePrivate(spec);
    }

    private static X509Certificate generateCertificateFromDER(byte[] certBytes) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
    }
}
