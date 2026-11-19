package com.ecommerce.FerreViky.ratelimit.provider;

import com.ecommerce.FerreViky.ratelimit.RateLimitType;
import io.github.bucket4j.Bucket;

public interface BucketProvider {
    Bucket bucketFor(String key, RateLimitType type);
}