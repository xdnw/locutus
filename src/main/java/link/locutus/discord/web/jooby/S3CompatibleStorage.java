package link.locutus.discord.web.jooby;

import link.locutus.discord.config.Settings;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class S3CompatibleStorage implements CloudStorage {

    private final Provider provider;
    private final S3Client s3Client;
    private final String bucketName;
    private final String publicBaseUrl;
    private final String awsRegion;
    private final String r2AccountId;

    private S3CompatibleStorage(Provider provider,
                                S3Client s3Client,
                                String bucketName,
                                String publicBaseUrl,
                                String awsRegion,
                                String r2AccountId) {
        this.provider = provider;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.awsRegion = awsRegion;
        this.r2AccountId = r2AccountId;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) return null;
        return url.replaceAll("/+$", "");
    }

    public enum Provider {
        AWS_S3("s3", "aws", "amazon") {
            @Override
            protected boolean isConfigured(Settings settings) {
                return hasAll(settings.WEB.S3.ACCESS_KEY,
                        settings.WEB.S3.SECRET_ACCESS_KEY,
                        settings.WEB.S3.REGION,
                        settings.WEB.S3.BUCKET);
            }

            @Override
            protected S3CompatibleStorage create(Settings settings) {
                return forAwsS3(
                        settings.WEB.S3.ACCESS_KEY,
                        settings.WEB.S3.SECRET_ACCESS_KEY,
                        settings.WEB.S3.BUCKET,
                        settings.WEB.S3.REGION,
                        settings.WEB.S3.BASE_URL
                );
            }
        },
        CLOUDFLARE_R2("r2", "cf", "cloudflare") {
            @Override
            protected boolean isConfigured(Settings settings) {
                return hasAll(settings.WEB.R2.ACCOUNT_ID,
                        settings.WEB.R2.ACCESS_KEY,
                        settings.WEB.R2.SECRET_ACCESS_KEY,
                        settings.WEB.R2.BUCKET);
            }

            @Override
            protected S3CompatibleStorage create(Settings settings) {
                return forCloudflareR2(
                        settings.WEB.R2.ACCOUNT_ID,
                        settings.WEB.R2.ACCESS_KEY,
                        settings.WEB.R2.SECRET_ACCESS_KEY,
                        settings.WEB.R2.BUCKET,
                        settings.WEB.R2.BASE_URL
                );
            }
        };

        private final Set<String> aliases;

        Provider(String... aliases) {
            this.aliases = Arrays.stream(aliases)
                    .map(alias -> alias.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        }

        protected abstract boolean isConfigured(Settings settings);

        protected abstract S3CompatibleStorage create(Settings settings);

        public static Optional<Provider> resolve(String name) {
            if (name == null || name.isBlank()) return Optional.empty();
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                    .filter(provider -> provider.aliases.contains(normalized))
                    .findFirst();
        }

        private static boolean hasAll(String... values) {
            return Arrays.stream(values).allMatch(v -> v != null && !v.isBlank());
        }
    }

    public static boolean isConfigured() {
        return Provider.resolve(Settings.INSTANCE.WEB.CONFLICTS.PROVIDER)
                .map(provider -> provider.isConfigured(Settings.INSTANCE))
                .orElse(false);
    }

    public static CloudStorage setupAuto() {
        Provider provider = Provider.resolve(Settings.INSTANCE.WEB.CONFLICTS.PROVIDER)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or missing cloud provider"));
        if (!provider.isConfigured(Settings.INSTANCE)) {
            throw new IllegalArgumentException(provider.name() + " configuration is incomplete");
        }
        return provider.create(Settings.INSTANCE);
    }

    public static S3CompatibleStorage setupAwsS3() {
        Provider provider = Provider.AWS_S3;
        if (!provider.isConfigured(Settings.INSTANCE)) {
            throw new IllegalArgumentException(provider.name() + " configuration is incomplete");
        }
        return provider.create(Settings.INSTANCE);
    }

    public static S3CompatibleStorage setupCloudflareR2() {
        Provider provider = Provider.CLOUDFLARE_R2;
        if (!provider.isConfigured(Settings.INSTANCE)) {
            throw new IllegalArgumentException(provider.name() + " configuration is incomplete");
        }
        return provider.create(Settings.INSTANCE);
    }

    public static S3CompatibleStorage forCloudflareR2(String accountId,
                                                      String accessKey,
                                                      String secretKey,
                                                      String bucketName,
                                                      String publicBaseUrl) {
        S3Client client = buildClient(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
                Region.of("auto"),
                URI.create(String.format("https://%s.r2.cloudflarestorage.com", accountId)),
                S3Configuration.builder().pathStyleAccessEnabled(true).build()
        );

        return new S3CompatibleStorage(Provider.CLOUDFLARE_R2, client, bucketName, publicBaseUrl, null, accountId);
    }

    public static S3CompatibleStorage forAwsS3(String accessKey,
                                               String secretKey,
                                               String bucketName,
                                               String region,
                                               String publicBaseUrl) {
        S3Client client = buildClient(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
                Region.of(region),
                null,
                null
        );

        return new S3CompatibleStorage(Provider.AWS_S3, client, bucketName, publicBaseUrl, region, null);
    }

    private static S3Client buildClient(StaticCredentialsProvider credentialsProvider,
                                        Region region,
                                        URI endpoint,
                                        S3Configuration configuration) {
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(region);

        if (endpoint != null) builder.endpointOverride(endpoint);
        if (configuration != null) builder.serviceConfiguration(configuration);

        return builder.build();
    }

    @Override
    public void putObject(String key, byte[] data, long maxAge) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .cacheControl("max-age=" + maxAge)
                .contentLength((long) data.length)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(data));
    }

    @Override
    public byte[] getObject(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObjectAsBytes(request).asByteArray();
    }

    @Override
    public String getLink(String key) {
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        if (publicBaseUrl != null) {
            return String.format("%s/%s", publicBaseUrl, encodedKey);
        }

        return switch (provider) {
            case CLOUDFLARE_R2 -> String.format(
                    "https://%s.r2.cloudflarestorage.com/%s/%s",
                    r2AccountId, bucketName, encodedKey);
            case AWS_S3 -> {
                String host = "us-east-1".equals(awsRegion) ? "s3.amazonaws.com" : "s3.%s.amazonaws.com".formatted(awsRegion);
                yield String.format("https://%s.%s/%s", bucketName, host, encodedKey);
            }
        };
    }

    @Override
    public void deleteObject(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(request);
    }

    @Override
    public List<CloudItem> getObjects() {
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());

        return response.contents().stream()
                .map(this::mapToCloudItem)
                .toList();
    }

    private CloudItem mapToCloudItem(S3Object object) {
        long lastModifiedMillis = object.lastModified() != null
                ? object.lastModified().toEpochMilli()
                : 0L;
        return new CloudItem(object.key(), lastModifiedMillis);
    }

    @Override
    public void close() {
        s3Client.close();
    }
}