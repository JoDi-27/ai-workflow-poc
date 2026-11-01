package aiworkflow.engine.port.in;

/**
 * The operations a driver calls (docs/engine/design.md section 2). One method per use case; the
 * Driver API item adds the others.
 */
public interface DriverOperations {

    Outcome<StatusView> status(Caller caller, StatusQuery query);
}
