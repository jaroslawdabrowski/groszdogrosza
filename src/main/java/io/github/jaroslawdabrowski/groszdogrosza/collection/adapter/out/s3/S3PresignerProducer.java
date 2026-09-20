package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.out.s3;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@code quarkus-amazon-s3} injects a plain {@code S3Client}/{@code S3AsyncClient} but has
 * no CDI producer for {@link S3Presigner} (a separate class within the same {@code s3}
 * artifact, not a client) - built by hand here, same pattern as
 * {@code CognitoIdentityProviderClientProducer}. Reuses the DynamoDB region setting rather
 * than a third region property.
 *
 * <p>In dev mode, Dev Services starts a shared Localstack container for both
 * {@code quarkus-amazon-dynamodb} and {@code quarkus-amazon-s3} and publishes its address as
 * {@code quarkus.s3.endpoint-override} - the CDI-produced {@code S3Client} picks that up
 * automatically (it's the extension's own client), but a hand-built {@code S3Presigner} has
 * to read it explicitly or every presigned URL would point at real AWS instead of the local
 * container. Empty/absent in prod, where this config key is never set.
 */
@ApplicationScoped
public class S3PresignerProducer {

    @ConfigProperty(name = "quarkus.dynamodb.aws.region", defaultValue = "eu-central-1")
    String region;

    @ConfigProperty(name = "quarkus.s3.endpoint-override")
    Optional<URI> endpointOverride;

    @Produces
    @ApplicationScoped
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
        // Path-style addressing (bucket in the path, not a virtual-hosted subdomain) is
        // required against Localstack - "<bucket>.localhost" doesn't resolve.
        endpointOverride.ifPresent(uri -> builder.endpointOverride(uri).serviceConfiguration(
                software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build()));
        return builder.build();
    }
}
