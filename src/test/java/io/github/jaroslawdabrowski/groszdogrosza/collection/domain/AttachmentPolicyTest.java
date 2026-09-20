package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AttachmentPolicyTest {

    @Test
    void acceptsAnAllowedImageUnderTheSizeLimit() {
        AttachmentPolicy.validate("image/jpeg", 5 * 1024 * 1024);
    }

    @Test
    void acceptsAPdf() {
        AttachmentPolicy.validate("application/pdf", 1024);
    }

    @Test
    void rejectsAnUnsupportedContentType() {
        assertThrows(UnsupportedAttachmentException.class, () -> AttachmentPolicy.validate("application/zip", 1024));
    }

    @Test
    void rejectsAFileOverTenMegabytes() {
        assertThrows(UnsupportedAttachmentException.class,
                () -> AttachmentPolicy.validate("image/png", AttachmentPolicy.MAX_SIZE_BYTES + 1));
    }

    @Test
    void rejectsAZeroOrNegativeSize() {
        assertThrows(UnsupportedAttachmentException.class, () -> AttachmentPolicy.validate("image/png", 0));
    }

    @Test
    void isImageDistinguishesImagesFromOtherAllowedTypes() {
        assertTrue(AttachmentPolicy.isImage("image/heic"));
        assertFalse(AttachmentPolicy.isImage("application/pdf"));
    }
}
