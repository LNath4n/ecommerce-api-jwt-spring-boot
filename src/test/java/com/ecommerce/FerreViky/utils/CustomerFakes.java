package com.ecommerce.FerreViky.utils;

import com.ecommerce.FerreViky.models.Customer;
import net.datafaker.Faker;

import java.util.function.Consumer;

public final class CustomerFakes {

    private static final Faker faker = new Faker();

    private CustomerFakes() {}

    public static Customer fakeCustomer() {
        return fakeCustomer(customer -> {});
    }

    public static Customer fakeCustomer(Consumer<Customer> changes) {
        Customer customer = new Customer();
        customer.setId(faker.number().numberBetween(1L, 100_000L));
        customer.setEmail(faker.internet().safeEmailAddress());
        customer.setActive(true);
        customer.setName("Nathan");
        customer.setPassword("UnSaludo");
        customer.setPhone("5551234567");

        changes.accept(customer);
        return customer;
    }
}
