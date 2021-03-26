package com.michelin.ns4kafka.models;

import com.michelin.ns4kafka.validation.ConnectValidator;
import com.michelin.ns4kafka.validation.TopicValidator;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Namespace {
    private final String apiVersion = "v1";
    private final String kind = "Namespace";
    @Valid
    @NotNull
    private ObjectMeta metadata;

    @Valid
    @NotNull
    private NamespaceSpec spec;

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public static class NamespaceSpec {
        private String kafkaUser;
        private TopicValidator topicValidator;
        private ConnectValidator connectValidator;
        //private ResourceQuota quota;
    }


}