package link.locutus.discord.web.jooby;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import jakarta.xml.bind.DatatypeConverter;
import link.locutus.discord.config.Settings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AwsManager {
    private final BasicAWSCredentials credentials;
    private final AmazonS3 s3Client;
    private final String bucketName;
    private final String region;

    public AwsManager(String accessKey, String secretKey, String bucketName, String region) {
        this.region = region;
        this.credentials = new BasicAWSCredentials(
                accessKey,
                secretKey
        );

        // put "test": "Hello World" in s3 bucket
        this.s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region)
                .build();

        this.bucketName = bucketName;
    }

    public void putObject(String key, byte[] data) {
        ObjectMetadata metadata = new ObjectMetadata();

        metadata.setContentLength(data.length);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        // Put the object in the bucket
        s3Client.putObject(new PutObjectRequest(bucketName, key, inputStream, metadata));
    }

    private String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            byte[] digest = md.digest();
            return DatatypeConverter.printHexBinary(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getObject(String key) throws IOException {
        return s3Client.getObject(bucketName, key).getObjectContent().readAllBytes();
    }

    public String getETag(AmazonS3 s3Client, String bucketName, String key) {
        ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, key);
        return metadata.getETag();
    }

    public String getLink(String key) {
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }
}
