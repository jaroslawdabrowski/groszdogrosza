package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.application;

import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.PaymentInfo;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.FindSchoolClassByPublicTokenUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.GetSchoolClassUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.IsClassTreasurerUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.ListTreasurerClassesUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.UpdateClassPaymentInfoUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.out.SchoolClassRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@ApplicationScoped
public class SchoolClassService implements GetSchoolClassUseCase, FindSchoolClassByPublicTokenUseCase,
        UpdateClassPaymentInfoUseCase, IsClassTreasurerUseCase, ListTreasurerClassesUseCase {

    @Inject
    SchoolClassRepositoryPort schoolClassRepository;

    @Override
    public Optional<SchoolClass> getSchoolClass(String classId) {
        return schoolClassRepository.findById(classId);
    }

    @Override
    public Optional<SchoolClass> findByPublicToken(String publicToken) {
        if (publicToken == null || publicToken.isBlank()) {
            return Optional.empty();
        }
        return schoolClassRepository.findByPublicToken(publicToken);
    }

    @Override
    public SchoolClass updatePaymentInfo(String classId, String bankAccountNumber, String blikPhoneNumber) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new NoSuchElementException("No such class: " + classId));
        return schoolClassRepository.save(schoolClass.withPaymentInfo(new PaymentInfo(bankAccountNumber, blikPhoneNumber)));
    }

    @Override
    public boolean isTreasurer(String classId, String email) {
        return email != null && schoolClassRepository.isTreasurer(classId, email);
    }

    @Override
    public List<String> classIdsWhereTreasurer(String email) {
        return email == null ? List.of() : schoolClassRepository.findClassIdsWhereTreasurer(email);
    }
}
