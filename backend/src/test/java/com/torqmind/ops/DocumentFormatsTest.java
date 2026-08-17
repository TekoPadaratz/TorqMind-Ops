package com.torqmind.ops;

import com.torqmind.ops.shared.documents.DocumentFormats;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DocumentFormatsTest {

    @Test
    void formatsBrazilianDocumentsWithoutRejectingRgOrForeign() {
        Assertions.assertEquals("12.345.678/0001-90", DocumentFormats.cnpj("12345678000190"));
        Assertions.assertEquals("123.456.789-09", DocumentFormats.personDocument("12345678909"));
        Assertions.assertEquals("MG-12.345", DocumentFormats.personDocument("MG-12.345"));
        Assertions.assertEquals("AB1234567", DocumentFormats.personDocument("AB1234567"));
        Assertions.assertEquals("ABC1D23", DocumentFormats.plate("abc1d23"));
        Assertions.assertNull(DocumentFormats.cnpj("  "));
    }
}
