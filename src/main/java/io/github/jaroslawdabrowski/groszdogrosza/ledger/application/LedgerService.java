package io.github.jaroslawdabrowski.groszdogrosza.ledger.application;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetFullLedgerUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetLedgerForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.RecordLedgerEntryUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.out.LedgerRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class LedgerService implements GetLedgerForStudentUseCase, RecordLedgerEntryUseCase, GetFullLedgerUseCase {

    @Inject
    LedgerRepositoryPort ledgerRepository;

    @Override
    public List<LedgerEntry> getLedgerFor(String studentId) {
        return ledgerRepository.findByStudentId(studentId);
    }

    @Override
    public List<LedgerEntry> getFullLedger() {
        return ledgerRepository.findAll();
    }

    @Override
    public LedgerEntry record(String studentId, LedgerEventType eventType, Map<String, String> params) {
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID().toString(), studentId, eventType, Instant.now(), params);
        return ledgerRepository.save(entry);
    }
}
