package com.torqmind.ops;

import com.torqmind.ops.application.tenant.TenantResolver;
import com.torqmind.ops.infrastructure.security.AppUserPrincipal;
import com.torqmind.ops.shared.api.ForbiddenException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class TenantResolverTest {

    private final TenantResolver resolver = new TenantResolver();

    @Test
    void managerCannotAccessOtherBranch() {
        AppUserPrincipal manager = new AppUserPrincipal(UUID.randomUUID(), "g", "MANAGER", 1L, 10L);
        Assertions.assertThrows(ForbiddenException.class, () -> resolver.assertCanAccess(manager, 1L, 99L));
        Assertions.assertDoesNotThrow(() -> resolver.assertCanAccess(manager, 1L, 10L));
    }

    @Test
    void ownerCannotAccessOtherCompany() {
        AppUserPrincipal owner = new AppUserPrincipal(UUID.randomUUID(), "d", "OWNER", 1L, null);
        Assertions.assertThrows(ForbiddenException.class, () -> resolver.assertCanAccess(owner, 2L, 1L));
    }

    @Test
    void masterAccessesAll() {
        AppUserPrincipal master = new AppUserPrincipal(UUID.randomUUID(), "a", "MASTER", null, null);
        Assertions.assertDoesNotThrow(() -> resolver.assertCanAccess(master, 9L, 9L));
    }
}
