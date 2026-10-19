package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.platform.security.ClassContext;
import io.github.jaroslawdabrowski.groszdogrosza.platform.web.ClassOverviewAssembler;
import io.github.jaroslawdabrowski.groszdogrosza.platform.web.PublicOverviewResponse;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.PaymentInfo;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.GetSchoolClassUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.UpdateClassPaymentInfoUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The class itself, for its members (ClassAccessFilter has already checked membership). */
@Path("/api/classes/{classId}")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class SchoolClassResource {

    @Inject
    GetSchoolClassUseCase getSchoolClassUseCase;

    @Inject
    UpdateClassPaymentInfoUseCase updateClassPaymentInfoUseCase;

    @Inject
    ClassOverviewAssembler classOverviewAssembler;

    @Inject
    ClassContext classContext;

    /** Name and payment details for every member; the public-link token only for treasurers
     *  (they're the ones who share it). */
    @GET
    public SchoolClassResponse get() {
        return SchoolClassResponse.from(requireClass(), classContext.isTreasurer());
    }

    /** Start for a logged-in member: the same aggregate overview as the public link, plus
     *  their own child's status on each collection. */
    @GET
    @Path("/overview")
    public PublicOverviewResponse overview() {
        return classOverviewAssembler.build(requireClass(), classContext.firstOwnStudentId());
    }

    @PUT
    @Path("/payment-info")
    public SchoolClassResponse updatePaymentInfo(PaymentInfoRequest request) {
        classContext.requireTreasurer();
        SchoolClass updated = updateClassPaymentInfoUseCase.updatePaymentInfo(classContext.classId(),
                request.bankAccountNumber(), request.blikPhoneNumber());
        return SchoolClassResponse.from(updated, true);
    }

    private SchoolClass requireClass() {
        return getSchoolClassUseCase.getSchoolClass(classContext.classId())
                .orElseThrow(() -> new NotFoundException("No such class"));
    }

    public record PaymentInfoRequest(String bankAccountNumber, String blikPhoneNumber) {
    }

    public record SchoolClassResponse(String id, String name, String bankAccountNumber, String blikPhoneNumber,
            String publicToken) {

        static SchoolClassResponse from(SchoolClass schoolClass, boolean treasurer) {
            PaymentInfo paymentInfo = schoolClass.paymentInfo();
            return new SchoolClassResponse(schoolClass.id(), schoolClass.name(),
                    paymentInfo == null ? null : paymentInfo.bankAccountNumber(),
                    paymentInfo == null ? null : paymentInfo.blikPhoneNumber(),
                    treasurer ? schoolClass.publicToken() : null);
        }
    }
}
