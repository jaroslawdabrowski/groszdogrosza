package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.in.scheduler;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.in.PollBankStatementsUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * The production trigger for a poll cycle. Deliberately NOT under {@code /api/*} - it must
 * stay reachable without a Cognito/Keycloak bearer token, since the caller in AWS is an
 * EventBridge Scheduler cron rule hitting the Lambda Function URL directly, not a logged-in
 * parent's browser. Protected instead by a shared secret header, checked here in the
 * adapter (not via quarkus-oidc) - see application.properties
 * (quarkus.http.auth.permission.internal, groszdogrosza.bankstatement.poll-secret) and
 * CLAUDE.md ("Why EventBridge instead of @Scheduled in Lambda") for the full reasoning.
 *
 * <p>In dev mode this endpoint still works (curl it directly to test), but
 * {@link BankStatementDevPoller} also fires automatically on a timer so you don't have to.
 */
@Path("/internal/bankstatement/poll")
public class BankStatementPollResource {

    private static final Logger LOG = Logger.getLogger(BankStatementPollResource.class);
    private static final String SECRET_HEADER = "X-Poll-Secret";

    @Inject
    PollBankStatementsUseCase pollBankStatementsUseCase;

    // Optional<String>, not String - a required (non-Optional) @ConfigProperty with no
    // default fails Quarkus startup outright when the property is present but empty (as
    // this one deliberately is until configured) - see ImapBankStatementFetchAdapter's
    // class javadoc for the same gotcha, confirmed by actually running quarkus:dev.
    @ConfigProperty(name = "groszdogrosza.bankstatement.poll-secret")
    Optional<String> expectedSecret;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response poll(@HeaderParam(SECRET_HEADER) String providedSecret) {
        if (expectedSecret.isEmpty() || expectedSecret.get().isBlank() || !secretMatches(expectedSecret.get(), providedSecret)) {
            LOG.warn("Rejected bankstatement poll request: missing/incorrect X-Poll-Secret header");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        PollBankStatementsUseCase.PollResult result = pollBankStatementsUseCase.pollAndProcess();
        return Response.ok(result).build();
    }

    // Constant-time comparison - this endpoint is deliberately reachable without OIDC auth
    // (see class javadoc), so a naive String.equals would leak a byte-by-byte timing signal
    // an attacker could use to guess the secret.
    private static boolean secretMatches(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
