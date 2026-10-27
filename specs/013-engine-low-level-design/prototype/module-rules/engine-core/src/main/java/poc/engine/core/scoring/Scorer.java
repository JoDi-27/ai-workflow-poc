package poc.engine.core.scoring;

import org.springframework.stereotype.Service;

@Service
public class Scorer {
    public int score(int earned, int deducted) { return earned - deducted; }
}
