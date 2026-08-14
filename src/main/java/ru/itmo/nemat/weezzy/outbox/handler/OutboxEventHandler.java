package ru.itmo.nemat.weezzy.outbox.handler;

import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

public interface OutboxEventHandler {

    OutboxEventType supportedType();

    void handle(OutboxEvent event);
}