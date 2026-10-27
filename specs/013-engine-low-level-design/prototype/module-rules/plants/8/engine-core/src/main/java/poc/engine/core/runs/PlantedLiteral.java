package poc.engine.core.runs;

class PlantedLiteral {
    static final String TOOL = "git";
    String provider() { return "claude merge " + TOOL; }
}
