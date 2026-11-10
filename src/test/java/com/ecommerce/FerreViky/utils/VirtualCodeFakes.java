package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.models.Product;
import com.ecommerce.FerreViky.models.VirtualCode;
import net.datafaker.Faker;

import java.util.function.Consumer;

public final class VirtualCodeFakes {

    private static final Faker faker = new Faker();

    private VirtualCodeFakes() {}

    public static VirtualCode fakeVirtualCode(Product product, String code) {
        return fakeVirtualCode(product, code, virtualCode -> {});
    }

    public static VirtualCode fakeVirtualCode(
            Product product, String code, Consumer<VirtualCode> changes) {
        VirtualCode virtualCode = new VirtualCode();
        virtualCode.setId(faker.number().numberBetween(1L, 100_000L));
        virtualCode.setProduct(product);
        virtualCode.setCode(code);
        virtualCode.setStatus(VirtualCode.CodeStatus.AVAILABLE);

        changes.accept(virtualCode);
        return virtualCode;
    }
}
