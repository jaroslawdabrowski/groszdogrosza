package io.github.jaroslawdabrowski.groszdogrosza.parent.application;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.PaymentInfo;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.TooManyParentsException;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateCognitoAccountUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.DeleteParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentByEmailUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetTreasurerPaymentInfoUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ResendCognitoInvitationUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.UpdateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.UpdatePaymentInfoUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.CognitoAccountManagementPort;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.ParentRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ParentService implements CreateParentUseCase, GetParentUseCase, GetParentByEmailUseCase,
        ListParentsUseCase, ListParentsForStudentUseCase, UpdatePaymentInfoUseCase, GetTreasurerPaymentInfoUseCase,
        CreateCognitoAccountUseCase, ResendCognitoInvitationUseCase, UpdateParentUseCase, DeleteParentUseCase {

    @Inject
    ParentRepositoryPort parentRepository;

    @Inject
    CognitoAccountManagementPort cognitoAccountManagementPort;

    @Override
    public Parent createParent(String studentId, String firstName, String lastName, String email,
            String expectedSenderName, ParentRole role) {
        if (parentRepository.findByStudentId(studentId).size() >= 2) {
            throw new TooManyParentsException(studentId);
        }
        Parent parent = new Parent(UUID.randomUUID().toString(), studentId, firstName, lastName, email,
                expectedSenderName, null, role, null);
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
    public List<Parent> listParentsForStudent(String studentId) {
        return parentRepository.findByStudentId(studentId);
    }

    @Override
    public Parent updatePaymentInfo(String parentId, String bankAccountNumber, String blikPhoneNumber) {
        Parent parent = requireParent(parentId);
        Parent updated = new Parent(parent.id(), parent.studentId(), parent.firstName(), parent.lastName(), parent.email(),
                parent.expectedSenderName(), parent.cognitoSubjectId(), parent.role(),
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
        Parent updated = new Parent(parent.id(), parent.studentId(), firstName, lastName, email, expectedSenderName,
                parent.cognitoSubjectId(), parent.role(), parent.paymentInfo());
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
}
