# Notification Service

This service handles notifications in the Finance Management system. It consumes events from Kafka topics and sends notifications via email, SMS, or push notifications.

## Kafka Integration

The NotificationService consumes events from the following Kafka topics:

- `user-notifications`: User events that should trigger notifications

## Supported Event Types

The service handles the following event types:

- `USER_CREATED`: Sends a welcome email to the user
- `USER_DELETED`: Sends an account deletion confirmation
- `PASSWORD_CHANGED`: Sends a password change notification

## Notification Channels

The service supports the following notification channels:

- Email notifications
- SMS notifications
- Push notifications

## REST API Endpoints

The service provides the following REST endpoints for testing:

### Health Check

```
GET /api/notifications/health
```

### Test Email Notification

```
POST /api/notifications/test/email?to={email}&subject={subject}&body={body}
```

### Test SMS Notification

```
POST /api/notifications/test/sms?phoneNumber={phone}&message={message}
```

### Test Push Notification

```
POST /api/notifications/test/push?userId={userId}&title={title}&body={body}
```

### Test User Event

```
POST /api/notifications/test/user-event?userId={userId}&email={email}&fullName={fullName}&eventType={eventType}&message={message}
```

## Setup

1. Make sure Kafka is running (see docker-compose.yml in the environment directory)
2. Configure the bootstrap servers in application.yaml
3. Start the service

## Usage from Other Services

To send events from other services to this notification service, you can:

1. Use the EventFactory class to create standardized events
2. Use KafkaTemplate to send events to the appropriate topics
3. Ensure the events follow the FinanceEvent schema

Example code for sending an event from another service:

```java
@Service
@RequiredArgsConstructor
public class YourService {
    private final KafkaTemplate<String, FinanceEvent> kafkaTemplate;
    
    public void processTransaction(Transaction transaction) {
        // Business logic...
        
        // Send notification event
        FinanceEvent event = EventFactory.createTransactionEvent(
            transaction.getUserId(),
            transaction.getAmount(),
            transaction.getCategory(),
            transaction.getDescription()
        );
        
        kafkaTemplate.send("transaction-events", event.getEventId(), event);
    }
}
``` 