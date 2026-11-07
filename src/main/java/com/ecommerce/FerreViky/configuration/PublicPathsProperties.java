package com.ecommerce.FerreViky.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

/**
 * Single source of truth for public (unauthenticated) routes.
 *
 * <p>{@code app.security.public-paths} holds the routes common to every profile.
 * {@code app.security.public-paths-extra} holds profile-specific ones
 * (e.g. Swagger and uploads in dev).</p>
 */
@Component
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class PublicPathsProperties {

    private List<String> publicPaths = List.of();
    private List<String> publicPathsExtra = List.of();

    /**
     * Returns every public route for the active profile: the common ones
     * followed by the profile-specific extras.
     *
     * @return Ant-style path patterns that do not require authentication
     */
    public String[] all() {
        return Stream.concat(publicPaths.stream(), publicPathsExtra.stream())
                .toArray(String[]::new);
    }
}