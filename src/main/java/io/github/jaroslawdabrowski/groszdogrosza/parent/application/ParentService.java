package io.github.jaroslawdabrowski.groszdogrosza.parent.application;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.RecordLedgerEntryUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.PaymentInfo;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateCognitoAccountUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreditPiggyBankManuallyUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreditPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.DebitPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.DeleteParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentByEmailUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetTreasurerPaymentInfoUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ResendCognitoInvitationUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.UpdateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.UpdatePaymentInfoUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.CognitoAccountManagementPort;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.ParentRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ParentService implements CreateParentUseCase, GetParentUseCase, GetParentByEmailUseCase,
        ListParentsUseCase, CreditPiggyBankUseCase, DebitPiggyBankUseCase, CreditPiggyBankManuallyUseCase,
        UpdatePaymentInfoUseCase, GetTreasurerPaymentInfoUseCase, CreateCognitoAccountUseCase,
        ResendCognitoInvitationUseCase, UpdateParentUseCase, DeleteParentUseCase {

    @Inject
    ParentRepositoryPort parentRepository;

    @Inject
    RecordLedgerEntryUseCase recordLedgerEntryUseCase;

    @Inject
    CognitoAccountManagementPort cognitoAccountManagementPort;

    @Override
    public Parent createParent(String firstName, String lastName, String email, String expectedSenderName,
            ParentRole role) {
        Parent parent = new Parent(UUID.randomUUID().toString(), firstName, lastName, email,
                expectedSenderName, null, role, BigDecimal.ZERO, null);
        return parentRepository.save(parent);
    }

    @Override
    public Optional<Parent> getParent(String parentId) {
        return parentRepository.findById(parentId);
    }

    @Override
    public Optional<Parent> getParentByEmail(String email) {
        return parentRepository.findByEmail(email);
    }

    @Override
    public List<Parent> listParents() {
        return parentRepository.findAll();
    }

    @Override
    public Parent creditPiggyBank(String parentId, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cannot credit a negative amount: " + amount);
        }
        Parent parent = requireParent(parentId);
        Parent updated = withPiggyBankBalance(parent, parent.piggyBankBalance().add(amount));
        return parentRepository.save(updated);
    }

    @Override
    public Parent debitPiggyBank(String parentId, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cannot debit a negative amount: " + amount);
        }
        Parent parent = requireParent(parentId);
        BigDecimal newBalance = parent.piggyBankBalance().subtract(amount);
        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Cannot debit " + amount + " from piggy bank balance " + parent.piggyBankBalance()
                            + " for parent " + parentId);
        }
        Parent updated = withPiggyBankBalance(parent, newBalance);
        return parentRepository.save(updated);
    }

    @Override
    public Parent creditPiggyBankManually(String parentId, BigDecimal amount) {
        Parent updated = creditPiggyBank(parentId, amount);
        recordLedgerEntryUseCase.record(parentId, LedgerEventType.PIGGY_BANK_CREDITED,
                Map.of("amount", amount.toPlainString()));
        return updated;
    }

    @Override
    public Parent updatePaymentInfo(String parentId, String bankAccountNumber, String blikPhoneNumber) {
        Parent parent = requireParent(parentId);
        Parent updated = new Parent(parent.id(), parent.firstName(), parent.lastName(), parent.email(),
                parent.expectedSenderName(), parent.cognitoSubjectId(), parent.role(), parent.piggyBankBalance(),
                new PaymentInfo(bankAccountNumber, blikPhoneNumber));
        return parentRepository.save(updated);
    }

    @Override
    public Optional<PaymentInfo> getTreasurerPaymentInfo() {
        return parentRepository.findAll().stream()
                .filter(parent -> parent.role() == ParentRole.TREASURER)
                .map(Parent::paymentInfo)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    @Override
    public Parent updateParent(String parentId, String firstName, String lastName, String email, String expectedSenderName) {
        Parent parent = requireParent(parentId);
        Parent updated = new Parent(parent.id(), firstName, lastName, email, expectedSenderName,
                parent.cognitoSubjectId(), parent.role(), parent.piggyBankBalance(), parent.paymentInfo());
        return parentRepository.save(updated);
    }

    @Override
    public void deleteParent(String parentId) {
        requireParent(parentId);
        parentRepository.deleteById(parentId);
    }

    @Override
    public void createCognitoAccount(String parentId) {
        Parent parent = requireParent(parentId);
        cognitoAccountManagementPort.createAccount(parent.email());
    }

    @Override
    public void resendCognitoInvitation(String parentId) {
        Parent parent = requireParent(parentId);
        cognitoAccountManagementPort.resendInvitation(parent.email());
    }

    private Parent requireParent(String parentId) {
        return parentRepository.findById(parentId)
                .orElseThrow(() -> new NoSuchElementException("No such parent: " + parentId));
    }

    private static Parent withPiggyBankBalance(Parent parent, BigDecimal newBalance) {
        return new Parent(parent.id(), parent.firstName(), parent.lastName(), parent.email(),
                parent.expectedSenderName(), parent.cognitoSubjectId(), parent.role(), newBalance,
                parent.paymentInfo());
    }
}
