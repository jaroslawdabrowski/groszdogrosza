package io.github.jaroslawdabrowski.groszdogrosza.platform.persistence;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;

/**
 * Creates the collection attachments bucket on startup if it doesn't exist yet - same
 * "make `quarkus:dev` work with zero manual setup" role as {@link DynamoDbTableInitializer},
 * against the same shared Dev Services Localstack container (quarkus-amazon-s3 and
 * quarkus-amazon-dynamodb reuse one container automatically). In real AWS the bucket is
 * created once by Terraform (aws_s3_bucket.attachments) instead, and the Lambda execution
 * role deliberately has no s3:CreateBucket - class-level {@code @IfBuildProfile("dev")}
 * (not method-level - see DynamoDbTableInitializer's javadoc for why that distinction
 * actually matters) keeps this bean out of the packaged Lambda entirely.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class S3BucketInitializer {

    private static final Logger LOG = Logger.getLogger(S3BucketInitializer.class);

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "groszdogrosza.attachments.bucket-name")
    String bucketName;

    void onStart(@Observes StartupEvent event) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            LOG.infof("Created S3 bucket '%s'", bucketName);
        } catch (BucketAlreadyOwnedByYouException e) {
            LOG.debugf("S3 bucket '%s' already exists", bucketName);
        }

        // The browser PUTs/GETs the presigned URL directly against Localstack, a different
        // origin (127.0.0.1:<port>) than the app itself (localhost:8080/4200) - without CORS
        // the PUT's preflight fails outright (ERR_FAILED, no response at all). Real AWS gets
        // its CORS config from Terraform (aws_s3_bucket_cors_configuration.attachments); "*"
        // here is fine since this bucket only exists inside a throwaway local container.
        s3Client.putBucketCors(PutBucketCorsRequest.builder()
                .bucket(bucketName)
                .corsConfiguration(CORSConfiguration.builder()
                        .corsRules(CORSRule.builder()
                                .allowedMethods("GET", "PUT")
                                .allowedOrigins("*")
                                .allowedHeaders("*")
                                .build())
                        .build())
                .build());
    }
}
