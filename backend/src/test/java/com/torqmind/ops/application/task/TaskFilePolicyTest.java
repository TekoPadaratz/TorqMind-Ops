package com.torqmind.ops.application.task;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TaskFilePolicyTest {

    @Test
    void recognizesRealPhotoByContent() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        TaskFilePolicy.InspectedFile inspected = TaskFilePolicy.inspect(png);
        Assertions.assertTrue(inspected.photo());
        Assertions.assertEquals("image/png", inspected.mimeType());
    }

    @Test
    void pdfDoesNotSatisfyPhotoRequirement() {
        byte[] pdf = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        TaskFilePolicy.InspectedFile inspected = TaskFilePolicy.inspect(pdf);
        Assertions.assertFalse(inspected.photo());
        Assertions.assertEquals("application/pdf", inspected.mimeType());
    }

    @Test
    void rejectsContentThatOnlyClaimsToBeAnImage() {
        byte[] fake = "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> TaskFilePolicy.inspect(fake));
    }
}
