package poc.engine.core.runs;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class RunEntity {
    @Id
    private String id;
    private String state;

    protected RunEntity() {}

    public RunEntity(String id, String state) { this.id = id; this.state = state; }

    public String id() { return id; }
    public String state() { return state; }
}
