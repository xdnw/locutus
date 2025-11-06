package link.locutus.discord.web.jooby;

import link.locutus.discord.config.Settings;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public final class S3CompatibleStorage implements ICloudStorage {

    public enum Provider {
        AWS_S3,
        CLOUDFLARE_R2
    }

    private final Provider provider;
    private final S3Client s3Client;
    private final String bucketName;
    private final String publicBaseUrl; // optional for both providers
    private final String awsRegion;     // only for AWS S3
    private final String r2AccountId;   // only for R2

    private S3CompatibleStorage(
            Provider provider,
            S3Client s3Client,
            String bucketName,
            String publicBaseUrl,
            String awsRegion,
            String r2AccountId
    ) {
        this.provider = provider;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.publicBaseUrl = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? null
                : publicBaseUrl.replaceAll("/+$", "");
        this.awsRegion = awsRegion;
        this.r2AccountId = r2AccountId;
    }

    public static ICloudStorage setupAwsS3() {
        String accessKey = Settings.INSTANCE.WEB.S3.ACCESS_KEY;
        String secretKey = Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY;
        String region = Settings.INSTANCE.WEB.S3.REGION;
        String bucket = Settings.INSTANCE.WEB.S3.BUCKET;
        String publicBaseUrl = Settings.INSTANCE.WEB.S3.BASE_URL;

        if (accessKey != null && secretKey != null && region != null && bucket != null
                && !accessKey.isEmpty() && !secretKey.isEmpty() && !region.isEmpty() && !bucket.isEmpty()) {
            return S3CompatibleStorage.forAwsS3(accessKey, secretKey, bucket, region, publicBaseUrl);
        }
        return null;
    }

    public static ICloudStorage setupCloudflareR2() {
        String accountId = Settings.INSTANCE.WEB.R2.ACCOUNT_ID;
        String accessKey = Settings.INSTANCE.WEB.R2.ACCESS_KEY;
        String secretKey = Settings.INSTANCE.WEB.R2.SECRET_ACCESS_KEY;
        String bucket = Settings.INSTANCE.WEB.R2.BUCKET;
        String publicBaseUrl = Settings.INSTANCE.WEB.R2.BASE_URL;

        if (accountId != null && accessKey != null && secretKey != null && bucket != null
                && !accountId.isEmpty() && !accessKey.isEmpty() && !secretKey.isEmpty() && !bucket.isEmpty()) {
            return S3CompatibleStorage.forCloudflareR2(accountId, accessKey, secretKey, bucket, publicBaseUrl);
        }
        return null;
    }

    // Factory for Cloudflare R2
    public static S3CompatibleStorage forCloudflareR2(
            String accountId,
            String accessKey,
            String secretKey,
            String bucketName,
            String publicBaseUrl
    ) {
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true) // R2 requires path-style
                .build();

        S3Client client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of("auto")) // Cloudflare signs with "auto"
                .endpointOverride(URI.create(String.format("https://%s.r2.cloudflarestorage.com", accountId)))
                .serviceConfiguration(s3Config)
                .build();

        return new S3CompatibleStorage(
                Provider.CLOUDFLARE_R2,
                client,
                bucketName,
                publicBaseUrl,
                null,          // awsRegion
                accountId      // r2AccountId
        );
    }

    // Factory for AWS S3
    public static S3CompatibleStorage forAwsS3(
            String accessKey,
            String secretKey,
            String bucketName,
            String region,
            String publicBaseUrl
    ) {
        S3Client client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build();

        return new S3CompatibleStorage(
                Provider.AWS_S3,
                client,
                bucketName,
                publicBaseUrl,
                region,     // awsRegion
                null        // r2AccountId
        );
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

        // If a public/cdn base URL is provided, use that for both providers
        if (publicBaseUrl != null) {
            return publicBaseUrl + "/" + encodedKey;
        }

        // Otherwise, compose a provider-specific URL
        if (provider == Provider.CLOUDFLARE_R2) {
            // This endpoint typically requires auth unless you expose it publicly via Workers or R2 public bucket
            return String.format("https://%s.r2.cloudflarestorage.com/%s/%s", r2AccountId, bucketName, encodedKey);
        } else {
            // Default AWS S3 virtual-hosted–style URL
            // Note: If the bucket is private, this will 403 without a presigned URL.
            String host = ("us-east-1".equals(awsRegion))
                    ? "s3.amazonaws.com"
                    : "s3." + awsRegion + ".amazonaws.com";
            return String.format("https://%s.%s/%s", bucketName, host, encodedKey);
        }
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
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        return response.contents()
                .stream()
                .map(this::mapToCloudItem)
                .collect(Collectors.toList());
    }

    private CloudItem mapToCloudItem(S3Object object) {
        long lastModifiedMillis = object.lastModified() != null
                ? object.lastModified().toEpochMilli()
                : 0L;

        return new CloudItem(object.key(), lastModifiedMillis);
    }

    public void close() {
        s3Client.close();
    }
}