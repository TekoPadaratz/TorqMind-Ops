package com.torqmind.ops;

import com.torqmind.ops.shared.media.MediaSignatures;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MediaSignaturesTest {

    @Test
    void detectsJpegAndRejectsPdfAsImage() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] pdf = "%PDF-1.4.....".getBytes();
        Assertions.assertEquals("image/jpeg", MediaSignatures.detect(jpeg));
        Assertions.assertEquals("application/pdf", MediaSignatures.detect(pdf));
        Assertions.assertTrue(MediaSignatures.isImage("image/jpeg"));
        Assertions.assertFalse(MediaSignatures.isImage("application/pdf"));
        Assertions.assertFalse(MediaSignatures.matchesDeclared("image/jpeg", "application/pdf"));
    }

    @Test
    void detectsWebmAudio() {
        byte[] webm = new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0, 0, 0, 0, 0};
        Assertions.assertEquals("audio/webm", MediaSignatures.detect(webm));
        Assertions.assertTrue(MediaSignatures.isAudio("audio/webm"));
    }

    @Test
    void emptyIsUnknown() {
        Assertions.assertNull(MediaSignatures.detect(new byte[] {1, 2, 3}));
    }
}
