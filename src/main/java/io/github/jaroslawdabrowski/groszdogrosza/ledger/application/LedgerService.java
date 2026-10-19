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
    public List<LedgerEntry> getLedgerFor(String classId, String studentId) {
        return ledgerRepository.findByStudentId(classId, studentId);
    }

    @Override
    public List<LedgerEntry> getFullLedger(String classId) {
        return ledgerRepository.findAllInClass(classId);
    }

    @Override
    public LedgerEntry record(String classId, String studentId, LedgerEventType eventType, Map<String, String> params) {
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID().toString(), classId, studentId, eventType, Instant.now(), params);
        return ledgerRepository.save(entry);
    }
}
