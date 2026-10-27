package poc.engine.core.typeddecisions;

import org.springframework.stereotype.Service;
import poc.engine.ports.out.decisions.DecisionPort;

@Service
public class TypedDecisions {
    private final DecisionPort provider;

    public TypedDecisions(DecisionPort provider) { this.provider = provider; }

    public String shadow(String question) { return provider.decide(question); }
}
