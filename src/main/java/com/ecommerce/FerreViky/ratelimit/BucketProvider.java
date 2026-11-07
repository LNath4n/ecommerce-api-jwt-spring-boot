package com.ecommerce.FerreViky.ratelimit;

import io.github.bucket4j.Bucket;

public interface BucketProvider {
    Bucket bucketFor(String key, RateLimitType type);
}