package io.github.jaroslawdabrowski.groszdogrosza.parent.application;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.TooManyParentsException;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateCognitoAccountUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.DeleteParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsByEmailUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ResendCognitoInvitationUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.UpdateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.CognitoAccountManagementPort;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.ParentRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ParentService implements CreateParentUseCase, GetParentUseCase, ListParentsByEmailUseCase,
        ListParentsUseCase, ListParentsForStudentUseCase, CreateCognitoAccountUseCase, ResendCognitoInvitationUseCase,
        UpdateParentUseCase, DeleteParentUseCase {

    @Inject
    ParentRepositoryPort parentRepository;

    @Inject
    CognitoAccountManagementPort cognitoAccountManagementPort;

    /** The caller has already checked that {@code studentId} is a student of {@code classId}. */
    @Override
    public Parent createParent(String classId, String studentId, String firstName, String lastName, String email,
            String expectedSenderName, ParentRole role) {
        if (parentRepository.findByStudentId(classId, studentId).size() >= 2) {
            throw new TooManyParentsException(studentId);
        }
        Parent parent = new Parent(UUID.randomUUID().toString(), classId, studentId, firstName, lastName, email,
                expectedSenderName, null, role, null);
        return parentRepository.save(parent);
    }

    @Override
    public Optional<Parent> getParent(String classId, String parentId) {
        return parentRepository.findById(classId, parentId);
    }

    @Override
    public List<Parent> listParentsByEmail(String email) {
        return email == null ? List.of() : parentRepository.findByEmail(email);
    }

    @Override
    public List<Parent> listParents(String classId) {
        return parentRepository.findAllInClass(classId);
    }

    @Override
    public List<Parent> listParentsForStudent(String classId, String studentId) {
        return parentRepository.findByStudentId(classId, studentId);
    }

    @Override
    public Parent updateParent(String classId, String parentId, String firstName, String lastName, String email,
            String expectedSenderName) {
        Parent parent = requireParent(classId, parentId);
        // role/paymentInfo are written back unchanged - see Parent's javadoc (rollback safety).
        Parent updated = new Parent(parent.id(), parent.classId(), parent.studentId(), firstName, lastName, email,
                expectedSenderName, parent.cognitoSubjectId(), parent.role(), parent.paymentInfo());
        return parentRepository.save(updated);
    }

    @Override
    public void deleteParent(String classId, String parentId) {
        requireParent(classId, parentId);
        parentRepository.deleteById(classId, parentId);
    }

    @Override
    public void createCognitoAccount(String classId, String parentId) {
        Parent parent = requireParent(classId, parentId);
        cognitoAccountManagementPort.createAccount(parent.email());
    }

    @Override
    public void resendCognitoInvitation(String classId, String parentId) {
        Parent parent = requireParent(classId, parentId);
        cognitoAccountManagementPort.resendInvitation(parent.email());
    }

    private Parent requireParent(String classId, String parentId) {
        return parentRepository.findById(classId, parentId)
                .orElseThrow(() -> new NoSuchElementException("No such parent: " + parentId));
    }
}
