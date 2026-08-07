package com.torqmind.ops;

import com.torqmind.ops.application.admin.PasswordPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsStrongPassword() {
        Assertions.assertDoesNotThrow(() -> PasswordPolicy.validate("TorqMind@123"));
        Assertions.assertDoesNotThrow(() -> PasswordPolicy.validate("Manager@123"));
    }

    @Test
    void rejectsShortPassword() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("Ab1"));
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("SomenteLetras"));
    }

    @Test
    void rejectsPasswordWithoutLetter() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("12345678"));
    }

    @Test
    void rejectsNull() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(null));
    }
}
