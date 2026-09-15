package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.in.scheduler;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.in.PollBankStatementsUseCase;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Dev-mode-only convenience so a local `quarkus:dev` session polls for new statement mail
 * automatically, without having to curl /internal/bankstatement/poll by hand.
 *
 * <p><b>Must never run in the packaged Lambda.</b> A Lambda instance only exists for the
 * duration of one invocation - there is no long-running process for Quarkus's
 * {@code @Scheduled} engine to tick in, so a real deployment relies entirely on AWS
 * EventBridge Scheduler calling {@link BankStatementPollResource}'s HTTP endpoint on a cron
 * schedule instead (see CLAUDE.md, "Why EventBridge instead of @Scheduled in Lambda"). The
 * {@code %dev.} profile prefix on {@code groszdogrosza.bankstatement.dev-poll-interval} in
 * application.properties is what keeps this inert outside dev mode - if that property is
 * unset (as it is in prod/the packaged jar), {@code every} below has no interval and
 * Quarkus never schedules this method at all. {@code @IfBuildProfile("dev")} is a second,
 * belt-and-suspenders guard - this bean (and therefore its @Scheduled method) simply does
 * not exist outside dev mode, so there is no scheduler wiring to misfire in prod at all.
 */
@Singleton
@IfBuildProfile("dev")
public class BankStatementDevPoller {

    private static final Logger LOG = Logger.getLogger(BankStatementDevPoller.class);

    @Inject
    PollBankStatementsUseCase pollBankStatementsUseCase;

    @Scheduled(every = "{groszdogrosza.bankstatement.dev-poll-interval}")
    void pollInDevMode() {
        LOG.info("Dev-mode bankstatement poll firing");
        PollBankStatementsUseCase.PollResult result = pollBankStatementsUseCase.pollAndProcess();
        LOG.infof("Dev-mode bankstatement poll result: %s", result);
    }
}
