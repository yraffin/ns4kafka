package com.michelin.ns4kafka.controllers;

import com.michelin.ns4kafka.models.ChangeConnectorState;
import com.michelin.ns4kafka.models.Connector;
import com.michelin.ns4kafka.models.Namespace;
import com.michelin.ns4kafka.models.ObjectMeta;
import com.michelin.ns4kafka.security.ResourceBasedSecurityRule;
import com.michelin.ns4kafka.services.KafkaConnectService;
import com.michelin.ns4kafka.services.NamespaceService;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.utils.SecurityService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConnectControllerTest {

    @Mock
    KafkaConnectService kafkaConnectService;

    @Mock
    NamespaceService namespaceService;
    @Mock
    ApplicationEventPublisher applicationEventPublisher;
    @Mock
    SecurityService securityService;

    @InjectMocks
    ConnectController connectController;

    @Test
    void listEmptyConnectors() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.findAllForNamespace(ns))
                .thenReturn(List.of());

        List<Connector> actual = connectController.list("test");
        Assertions.assertTrue(actual.isEmpty());
    }

    @Test
    void listMultipleConnectors() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.findAllForNamespace(ns))
                .thenReturn(List.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build(),
                        Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build()
                ));

        List<Connector> actual = connectController.list("test");
        Assertions.assertEquals(2, actual.size());
    }

    @Test
    void getConnectorEmpty() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.findByName(ns, "missing"))
                .thenReturn(Optional.empty());

        Optional<Connector> actual = connectController.getConnector("test", "missing");
        Assertions.assertTrue(actual.isEmpty());
    }

    @Test
    void getConnector() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.findByName(ns, "connect1"))
                .thenReturn(Optional.of(
                        Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build()
                ));

        Optional<Connector> actual = connectController.getConnector("test", "connect1");
        Assertions.assertTrue(actual.isPresent());
        Assertions.assertEquals("connect1", actual.get().getMetadata().getName());
    }

    @Test
    void deleteConnecortNotOwned() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(false);

        Assertions.assertThrows(ResourceValidationException.class, () -> connectController.deleteConnector("test", "connect1", false));
    }

    @Test
    void deleteConnectorOwned() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Connector connector = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.findByName(ns,"connect1"))
                .thenReturn(Optional.of(connector));
        when(securityService.username()).thenReturn(Optional.of("test-user"));
        when(securityService.hasRole(ResourceBasedSecurityRule.IS_ADMIN)).thenReturn(false);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        Assertions.assertDoesNotThrow(() -> connectController.deleteConnector("test", "connect1", false));
    }

    @Test
    void deleteConnectorOwnedDryRun() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);

        connectController.deleteConnector("test", "connect1", true);
        verify(kafkaConnectService, never()).delete(any(), any());
    }

    @Test
    void createConnectorNotOwner() {
        Connector connector = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(false);

        ResourceValidationException actual = Assertions.assertThrows(ResourceValidationException.class, () -> connectController.apply("test", connector, false));
        Assertions.assertLinesMatch(List.of("Invalid value connect1 for name: Namespace not OWNER of this connector"), actual.getValidationErrors());
    }

    @Test
    void createConnectorLocalErrors() {
        Connector connector = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(new HashMap<>()).build())
                .build();

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.validateLocally(ns, connector))
                .thenReturn(List.of("Local Validation Error 1"));

        ResourceValidationException actual = Assertions.assertThrows(ResourceValidationException.class, () -> connectController.apply("test", connector, false));
        Assertions.assertLinesMatch(List.of("Local Validation Error 1"), actual.getValidationErrors());
    }

    @Test
    void createConnectorRemoteErrors() {
        Connector connector = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(new HashMap<>()).build())
                .build();

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.validateLocally(ns, connector))
                .thenReturn(List.of());
        Mockito.when(kafkaConnectService.validateRemotely(ns, connector))
                .thenReturn(List.of("Remote Validation Error 1"));

        ResourceValidationException actual = Assertions.assertThrows(ResourceValidationException.class, () -> connectController.apply("test", connector, false));
        Assertions.assertLinesMatch(List.of("Remote Validation Error 1"), actual.getValidationErrors());
    }

    @Test
    void createConnectorSuccess() {
        Connector connector = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(new HashMap<>()).build())
                .build();
        Connector expected = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(Map.of("name", "connect1")).build())
                .status(Connector.ConnectorStatus.builder().state(Connector.TaskState.UNASSIGNED).build())
                .build();

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.validateLocally(ns, connector))
                .thenReturn(List.of());
        Mockito.when(kafkaConnectService.validateRemotely(ns, connector))
                .thenReturn(List.of());
        when(securityService.username()).thenReturn(Optional.of("test-user"));
        when(securityService.hasRole(ResourceBasedSecurityRule.IS_ADMIN)).thenReturn(false);
        doNothing().when(applicationEventPublisher).publishEvent(any());
        Mockito.when(kafkaConnectService.createOrUpdate(ns, connector))
                .thenReturn(expected);

        var response = connectController.apply("test", connector, false);
        Connector actual = response.body();

        Assertions.assertEquals("created", response.header("X-Ns4kafka-Result"));
        Assertions.assertEquals(expected.getStatus().getState(), actual.getStatus().getState());

    }

    @Test
    void createConnectorSuccess_AlreadyExists() {
        Connector connector = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(new HashMap<>()).build())
                .build();
        Connector expected = Connector.builder()
                .metadata(ObjectMeta.builder()
                        .namespace("test")
                        .cluster("local")
                        .name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(Map.of("name", "connect1")).build())
                .status(Connector.ConnectorStatus.builder().state(Connector.TaskState.UNASSIGNED).build())
                .build();

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.validateLocally(ns, connector))
                .thenReturn(List.of());
        Mockito.when(kafkaConnectService.validateRemotely(ns, connector))
                .thenReturn(List.of());
        Mockito.when(kafkaConnectService.findByName(ns, "connect1"))
                .thenReturn(Optional.of(connector));


        var response = connectController.apply("test", connector, false);
        Connector actual = response.body();

        Assertions.assertEquals("unchanged", response.header("X-Ns4kafka-Result"));
        Assertions.assertEquals(expected, actual);
        verify(kafkaConnectService,never()).createOrUpdate(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void createConnectorSuccess_Changed() {
        Connector connector = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(new HashMap<>()).build())
                .build();
        Connector connectorOld = Connector.builder().metadata(ObjectMeta.builder().name("connect1").labels(Map.of("label", "labelValue")).build()).build();
        Connector expected = Connector.builder()
                .metadata(ObjectMeta.builder()
                        .name("connect1")
                        .labels(Map.of("label", "labelValue"))
                        .build())
                .spec(Connector.ConnectorSpec.builder().config(Map.of("name", "connect1")).build())
                .status(Connector.ConnectorStatus.builder().state(Connector.TaskState.UNASSIGNED).build())
                .build();

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.validateLocally(ns, connector))
                .thenReturn(List.of());
        Mockito.when(kafkaConnectService.validateRemotely(ns, connector))
                .thenReturn(List.of());
        Mockito.when(kafkaConnectService.findByName(ns, "connect1"))
                .thenReturn(Optional.of(connectorOld));
        when(securityService.username()).thenReturn(Optional.of("test-user"));
        when(securityService.hasRole(ResourceBasedSecurityRule.IS_ADMIN)).thenReturn(false);
        doNothing().when(applicationEventPublisher).publishEvent(any());
        Mockito.when(kafkaConnectService.createOrUpdate(ns, connector))
                .thenReturn(expected);

        var response = connectController.apply("test", connector, false);
        Connector actual = response.body();

        Assertions.assertEquals("changed", response.header("X-Ns4kafka-Result"));
        Assertions.assertEquals(expected, actual);

    }

    @Test
    void createConnectorDryRun() {
        Connector connector = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(new HashMap<>()).build())
                .build();
        Connector expected = Connector.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(Connector.ConnectorSpec.builder().config(Map.of("name", "connect1")).build())
                .status(Connector.ConnectorStatus.builder().state(Connector.TaskState.UNASSIGNED).build())
                .build();

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.validateLocally(ns, connector))
                .thenReturn(List.of());
        Mockito.when(kafkaConnectService.validateRemotely(ns, connector))
                .thenReturn(List.of());

        var response = connectController.apply("test", connector, true);
        Connector actual = response.body();
        Assertions.assertEquals("created", response.header("X-Ns4kafka-Result"));
        verify(kafkaConnectService, never()).createOrUpdate(ns, connector);

    }

    @Test
    public void ImportConnector() {
       
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Connector connector1 = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();
        Connector connector2 = Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build();
        Connector connector3 = Connector.builder().metadata(ObjectMeta.builder().name("connect3").build()).build();

        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        
        when(kafkaConnectService.listUnsynchronizedConnectors(ns))
                .thenReturn(List.of(connector1, connector2));
        
        when(kafkaConnectService.createOrUpdate(ns, connector1)).thenReturn(connector1);
        when(kafkaConnectService.createOrUpdate(ns, connector2)).thenReturn(connector2);

        List<Connector> actual = connectController.importResources("test", false);
        Assertions.assertTrue(actual.stream()
                .anyMatch(c ->
                        c.getMetadata().getName().equals("connect1")
                ));
        Assertions.assertTrue(actual.stream()
                .anyMatch(c ->
                        c.getMetadata().getName().equals("connect2")
                ));
        Assertions.assertFalse(actual.stream()
                .anyMatch(c ->
                        c.getMetadata().getName().equals("connect3")
                ));
    }

    @Test
    public void ImportConnectorDryRun() {

        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Connector connector1 = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();
        Connector connector2 = Connector.builder().metadata(ObjectMeta.builder().name("connect2").build()).build();
        Connector connector3 = Connector.builder().metadata(ObjectMeta.builder().name("connect3").build()).build();

        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));

        when(kafkaConnectService.listUnsynchronizedConnectors(ns))
                .thenReturn(List.of(connector1, connector2));

        List<Connector> actual = connectController.importResources("test", true);
        Assertions.assertTrue(actual.stream()
                .anyMatch(c ->
                        c.getMetadata().getName().equals("connect1")
                ));
        Assertions.assertTrue(actual.stream()
                .anyMatch(c ->
                        c.getMetadata().getName().equals("connect2")
                ));
        Assertions.assertFalse(actual.stream()
                .anyMatch(c ->
                        c.getMetadata().getName().equals("connect3")
                ));
        verify(kafkaConnectService, never()).createOrUpdate(ns, connector1);
        verify(kafkaConnectService, never()).createOrUpdate(ns, connector2);
        verify(kafkaConnectService, never()).createOrUpdate(ns, connector3);
    }
    @Test
    void restartConnectorNotOwned() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(false);

        ChangeConnectorState restart = ChangeConnectorState.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(ChangeConnectorState.ChangeConnectorStateSpec.builder().action(ChangeConnectorState.ConnectorAction.restart).build())
                .build();

        Assertions.assertThrows(ResourceValidationException.class, () -> connectController.changeState("test", "connect1", restart));
    }

    @Test
    void restartConnectorNotExists() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.findByName(ns,"connect1"))
                .thenReturn(Optional.empty());

        ChangeConnectorState restart = ChangeConnectorState.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(ChangeConnectorState.ChangeConnectorStateSpec.builder().action(ChangeConnectorState.ConnectorAction.restart).build())
                .build();

        HttpResponse<ChangeConnectorState> actual = connectController.changeState("test", "connect1", restart);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, actual.getStatus());
        verify(kafkaConnectService,never()).restart(ArgumentMatchers.any(), ArgumentMatchers.any());
    }
    @Test
    void restartConnectorException() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Connector connector = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.findByName(ns,"connect1"))
                .thenReturn(Optional.of(connector));
        Mockito.when(kafkaConnectService.restart(ArgumentMatchers.any(),ArgumentMatchers.any()))
                .thenThrow(new HttpClientResponseException("Rebalancing", HttpResponse.status(HttpStatus.CONFLICT)));

        ChangeConnectorState restart = ChangeConnectorState.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(ChangeConnectorState.ChangeConnectorStateSpec.builder().action(ChangeConnectorState.ConnectorAction.restart).build())
                .build();

        HttpResponse<ChangeConnectorState> actual = connectController.changeState("test", "connect1", restart);

        Assertions.assertFalse(actual.body().getStatus().isSuccess());
        Assertions.assertEquals("Rebalancing", actual.body().getStatus().getErrorMessage());
    }

    @Test
    void restartConnectorOwned() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Connector connector = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.findByName(ns,"connect1"))
                .thenReturn(Optional.of(connector));
        Mockito.when(kafkaConnectService.restart(ArgumentMatchers.any(),ArgumentMatchers.any()))
                .thenReturn(HttpResponse.noContent());

        ChangeConnectorState changeConnectorState = ChangeConnectorState.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(ChangeConnectorState.ChangeConnectorStateSpec.builder().action(ChangeConnectorState.ConnectorAction.restart).build())
                .build();

        HttpResponse<ChangeConnectorState> actual = connectController.changeState("test", "connect1", changeConnectorState);

        Assertions.assertTrue(actual.body().getStatus().isSuccess());
        Assertions.assertEquals(HttpStatus.NO_CONTENT, actual.body().getStatus().getCode());
        Assertions.assertEquals("connect1", actual.body().getMetadata().getName());
    }
    @Test
    void pauseConnectorOwned() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Connector connector = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.findByName(ns,"connect1"))
                .thenReturn(Optional.of(connector));
        Mockito.when(kafkaConnectService.pause(ArgumentMatchers.any(),ArgumentMatchers.any()))
                .thenReturn(HttpResponse.noContent());

        ChangeConnectorState changeConnectorState = ChangeConnectorState.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(ChangeConnectorState.ChangeConnectorStateSpec
                        .builder()
                        .action(ChangeConnectorState.ConnectorAction.pause)
                        .build())
                .build();

        HttpResponse<ChangeConnectorState> actual = connectController.changeState("test", "connect1", changeConnectorState);

        Assertions.assertEquals(HttpStatus.NO_CONTENT, actual.body().getStatus().getCode());
        Assertions.assertEquals("connect1", actual.body().getMetadata().getName());
    }
    @Test
    void resumeConnectorOwned() {
        Namespace ns = Namespace.builder()
                .metadata(ObjectMeta.builder()
                        .name("test")
                        .cluster("local")
                        .build())
                .build();
        Connector connector = Connector.builder().metadata(ObjectMeta.builder().name("connect1").build()).build();
        Mockito.when(namespaceService.findByName("test"))
                .thenReturn(Optional.of(ns));
        Mockito.when(kafkaConnectService.isNamespaceOwnerOfConnect(ns, "connect1"))
                .thenReturn(true);
        Mockito.when(kafkaConnectService.findByName(ns,"connect1"))
                .thenReturn(Optional.of(connector));
        Mockito.when(kafkaConnectService.resume(ArgumentMatchers.any(),ArgumentMatchers.any()))
                .thenReturn(HttpResponse.noContent());

        ChangeConnectorState changeConnectorState = ChangeConnectorState.builder()
                .metadata(ObjectMeta.builder().name("connect1").build())
                .spec(ChangeConnectorState.ChangeConnectorStateSpec
                        .builder()
                        .action(ChangeConnectorState.ConnectorAction.resume)
                        .build())
                .build();

        HttpResponse<ChangeConnectorState> actual = connectController.changeState("test", "connect1", changeConnectorState);

        Assertions.assertEquals(HttpStatus.NO_CONTENT, actual.body().getStatus().getCode());
        Assertions.assertEquals("connect1", actual.body().getMetadata().getName());
    }
}
