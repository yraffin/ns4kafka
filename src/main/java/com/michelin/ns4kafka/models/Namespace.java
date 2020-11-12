package com.michelin.ns4kafka.models;

import com.michelin.ns4kafka.models.security.ResourceSecurityPolicy;
import io.micronaut.security.authentication.Authentication;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
public class Namespace {
    private String name;
    private String owner;
    private List<ResourceSecurityPolicy> policies;
    private int diskQuota;

}
