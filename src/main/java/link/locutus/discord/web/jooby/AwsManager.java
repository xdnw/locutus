package link.locutus.discord.web.jooby;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import jakarta.xml.bind.DatatypeConverter;
import link.locutus.discord.config.Settings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

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

    public void putObject(String key, byte[] data, long maxAge) {
        ObjectMetadata metadata = new ObjectMetadata();

        metadata.setContentLength(data.length);
        metadata.setCacheControl("max-age=" + maxAge);

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

    public static void main(String[] args) {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        String awsKey = Settings.INSTANCE.WEB.S3.ACCESS_KEY;
        String awsSecret = Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY;
        String bucketName = Settings.INSTANCE.WEB.S3.BUCKET;
        String region = Settings.INSTANCE.WEB.S3.REGION;

        // Write some code to list the items in the bucket
        AwsManager awsManager = new AwsManager(awsKey, awsSecret, bucketName, region);
//        ListObjectsV2Result result = awsManager.s3Client.listObjectsV2(bucketName);
//        List<S3ObjectSummary> objects = result.getObjectSummaries();
//        for (S3ObjectSummary os : objects) {
//            System.out.println("* " + os.getKey() + " - size: " + os.getSize() + " - last modified: " + os.getLastModified());
//        }

        // move conflicts/7.gzip to conflicts/n/189573/<uuid>.gzip


    }
    public void copyObject(String sourceKey, String destinationKey) {
        s3Client.copyObject(bucketName, sourceKey, bucketName, destinationKey);
    }

    public void deleteObject(String key) {
        s3Client.deleteObject(bucketName, key);
    }

    public void moveObject(String sourceKey, String destinationKey) {
        copyObject(sourceKey, destinationKey);
        deleteObject(sourceKey);
    }

    public List<S3ObjectSummary> getObjects() {
        ListObjectsV2Result result = s3Client.listObjectsV2(bucketName);
        return result.getObjectSummaries();
    }
}
