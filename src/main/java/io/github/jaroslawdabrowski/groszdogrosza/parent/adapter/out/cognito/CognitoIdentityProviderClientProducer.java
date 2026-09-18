package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.out.cognito;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * There's no Quarkiverse extension for Cognito *admin* operations (unlike DynamoDB), so this
 * client is built by hand rather than injected by an extension. It picks up the same sync
 * HTTP client (`url-connection-client`) and default AWS credentials chain that
 * `quarkus-amazon-dynamodb` uses under the hood - in the real Lambda that means the
 * function's own execution role, no extra config needed there. Reuses the DynamoDB client's
 * region setting rather than introducing a second region property - this app only ever
 * deploys to one region at a time.
 */
@ApplicationScoped
public class CognitoIdentityProviderClientProducer {

    @ConfigProperty(name = "quarkus.dynamodb.aws.region", defaultValue = "eu-central-1")
    String region;

    @Produces
    @ApplicationScoped
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }
}
