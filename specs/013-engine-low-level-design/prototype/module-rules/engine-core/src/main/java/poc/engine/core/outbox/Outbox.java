package poc.engine.core.outbox;

import org.springframework.stereotype.Service;
import poc.engine.ports.out.events.EventPublisher;

@Service
public class Outbox {
    private final EventPublisher publisher;

    public Outbox(EventPublisher publisher) { this.publisher = publisher; }

    public void relay(String payload) { publisher.publish("run-events", payload); }
}
