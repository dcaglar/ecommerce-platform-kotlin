# Outbox Relay to Kafka Cluster Flow

This diagram captures the complete flow happening between the Outbox Relay Job, the Kafka Cluster topics, all of the consumers, and the downstream services, exactly as presented in the SVG.

```mermaid
flowchart LR
    classDef job fill:#b2dfdb,stroke:#00897b,stroke-width:2px,color:#000
    classDef topic fill:#bbdefb,stroke:#1e88e5,stroke-width:2px,color:#000
    classDef consumer fill:#f3e5f5,stroke:#8e24aa,stroke-width:2px,color:#000
    classDef service fill:#fff9c4,stroke:#fbc02d,stroke-width:2px,color:#000
    classDef executor fill:#ffe0b2,stroke:#ef6c00,stroke-width:2px,color:#000

    %% Outbox Relay Job
    ORJ["<b>OUTBOX RELAY JOB</b><br/>transforms OutboxEvent to an EventEnvelope<br/>and publishes to kafka"]:::job

    %% KAFKA CLUSTER
    subgraph KafkaCluster ["<b>KAFKA CLUSTER</b>"]
        direction TB
        TopicGCC("Topic:<br/>Gateway.Capture.Command"):::topic
        TopicGCS("Topic:<br/>Gateway.Capture.Submit"):::topic
        TopicPPR("Topic:<br/>Payment.Psp.Result"):::topic
        TopicLJE("Topic:<br/>Ledger.JournalEntries.Recorded"):::topic
    end

    %% Consumers & Executors
    subgraph Consumers ["<b>CONSUMERS / EXECUTORS</b>"]
        direction TB
        CCE["<b>CaptureCommandExecutor</b><br/>(CAPTUREPSPEXECUTOR)"]:::executor
        CS_PSP_C["<b>CAPTURESUBMITPSPCONSUMER</b>"]:::consumer
        Psp_RC["<b>PspResultConsumer</b><br/>(PSPRESULTCONSUMER)"]:::consumer
        CPP_C["<b>CapturePspPerformedConsumer</b>"]:::consumer
        MPI_C["<b>GrossCaptureAllocationConsumer</b>"]:::consumer
        ITR_E["<b>InternalTransferRequestExecutor</b>"]:::executor
        Ledger_C["<b>Ledger Consumer</b>"]:::consumer
    end

    %% USE CASE IMPLEMENTATIONS
    subgraph Services ["<b>USE CASE IMPLEMENTATIONS</b>"]
        direction TB
        Svc_PCS["ProcesCaptureService"]:::service
        Svc_RCSS["RecordCaptureSubmissionService"]:::service
        Svc_PPRS["ProcessPspResultService"]:::service
        Svc_RITSS["RecordInternalTransferSubmissionService"]:::service
    end

    %% FROM ORJ TO TOPICS
    ORJ -- "1-if OutboxEventTypes.CAPTURE_REQUESTED,<br/>then publish EventEnvelope&lt;CaptureRequested&gt;" --> TopicGCC
    ORJ -- "2-if OutboxEventTypes.CAPTURE_SUBMITTED,<br/>then publish EventEnvelope&lt;CaptureSubmitted&gt;" --> TopicGCS

    ORJ -- "3-if OutboxEventTypes.PAYMENT_AUTHORIZED,<br/>then publish EventEnvelope&lt;PAYMENT_AUTHORIZED&gt;" --> TopicPPR
    ORJ -- "4-if OutboxEventTypes.CAPTURE_CONFIRMED,<br/>then publish EventEnvelope&lt;CaptureConfirmed&gt;" --> TopicPPR
    ORJ -- "5-if OutboxEventTypes.INTERNAL_TRANSFER_COMMAND,<br/>then publish EventEnvelope&lt;INTERNAL_TRANSFER_COMMAND&gt;" --> TopicPPR

    ORJ -- "6-if OutboxEventTypes.JOURNALENTRIES_RECORDED,<br/>then publish EventEnvelope&lt;JOURNALENTRIES_RECORDED&gt;" --> TopicLJE

    %% FROM TOPICS TO CONSUMERS
    TopicGCC -- "1.1-consumes<br/>EventEnvelope&lt;CaptureRequested&gt;" --> CCE
    TopicGCS -- "2.1-consumes<br/>EventEnvelope&lt;CaptureSubmitteed&gt;" --> CS_PSP_C
    
    TopicPPR -- "3.1-consumes<br/>EventEnvelope&lt;PAymentAuthorized&gt;" --> Psp_RC
    TopicPPR -- "4.1-consumes<br/>EventEnvelope&lt;CaptureConfirmed&gt;" --> Psp_RC
    TopicPPR -- "5.1-consumes<br/>EventEnvelope&lt;InternalTransferCommand&gt;" --> Psp_RC
    TopicPPR -- "ExternalAsyncCaptureToPspPerformed" --> CPP_C
    
    TopicLJE -- "6.1-consumes<br/>EventEnvelope&lt;JournalEntroesRecorded&gt;" --> Ledger_C
    
    TopicPPR -- "Sub-Seller Distribution" --> MPI_C
    TopicPPR -- "OutboxEvent: InternalTransferRequest" --> ITR_E

    %% FROM CONSUMERS TO SERVICES
    CCE -- "call the use case implementation" --> Svc_PCS
    CS_PSP_C -- "call use case implementation" --> Svc_RCSS
    Psp_RC -- "processAuthorized /<br/>processCaptureConfirmed /<br/>processInternalTransferCommand" --> Svc_PPRS
    MPI_C -- "recodrSubmission(splitArr)" --> Svc_RITSS
```
