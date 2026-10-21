package hello.definitions;

import com.networknt.schema.Error;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Spike 012 trial: parse definition YAML with Jackson 3 and validate it with networknt (draft 2020-12). */
public class Trial {

    static final ObjectMapper YAML = YAMLMapper.builder(new YAMLFactory())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public static void main(String[] args) throws Exception {
        var reference = Files.readString(Path.of(args[0]));
        var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(Trial.class.getResourceAsStream("/partial-schema.json"));
        System.out.println("jackson " + tools.jackson.databind.cfg.PackageVersion.VERSION);

        check("reference definition", reference, schema);
        check("unquoted no, yes, on, off as labels",
                reference.replace("labels: [poor, weak, good, exemplary]", "labels: [no, yes, on, off]"), schema);
        check("unquoted true, false as labels",
                reference.replace("labels: [poor, weak, good, exemplary]", "labels: [true, false]"), schema);
        check("duplicate top-level key", reference.replace("title: SDLC reference workflow",
                "title: SDLC reference workflow\ntitle: again"), schema);
        check("unknown top-level key", reference + "\nextra_key: 1\n", schema);
        check("transition missing 'to'", reference.replaceFirst("(?m)^(\\s+)to: implementation$", "$1# removed"), schema);
    }

    static void check(String name, String yaml, com.networknt.schema.Schema schema) {
        System.out.println("\n== " + name);
        JsonNode node;
        try {
            node = YAML.readTree(yaml);
        } catch (Exception e) {
            System.out.println("parse error: " + e.getMessage().replace("\n", " "));
            return;
        }
        var labels = node.path("typed_questions").path("code_quality_band").path("labels");
        System.out.println("code_quality_band.labels parsed as " + labels);
        var lines = lineIndex(yaml);
        var errors = schema.validate(node);
        System.out.println(errors.size() + " schema error(s)");
        for (Error e : errors) {
            var pointer = e.getInstanceLocation().toString();
            System.out.println("  " + pointer + " (line " + lineOf(lines, pointer) + "): " + e.getMessage());
        }
    }

    /** Maps each value's JSON pointer to its 1-based line, from the YAML token stream (the tree keeps no locations). */
    static Map<String, Integer> lineIndex(String yaml) {
        var index = new HashMap<String, Integer>();
        try (JsonParser p = YAML.createParser(yaml)) {
            Deque<String> path = new ArrayDeque<>();
            Deque<Integer> arrayIdx = new ArrayDeque<>();
            index.put("", 1);
            for (JsonToken t = p.nextToken(); t != null; t = p.nextToken()) {
                var ctx = p.streamReadContext();
                if (t == JsonToken.PROPERTY_NAME) continue;
                String here = pointer(ctx);
                index.putIfAbsent(here, p.currentTokenLocation().getLineNr());
            }
        } catch (Exception e) {
            // duplicate keys etc. are reported by the parse step
        }
        return index;
    }

    static String pointer(tools.jackson.core.TokenStreamContext ctx) {
        var parts = new ArrayDeque<String>();
        for (var c = ctx; c != null && !c.inRoot(); c = c.getParent()) {
            if (c.inObject() && c.currentName() != null) parts.push("/" + c.currentName());
            else if (c.inArray()) parts.push("/" + c.getCurrentIndex());
        }
        // a START_OBJECT/START_ARRAY token's context is the new container; its pointer is the parent's entry
        return String.join("", parts);
    }

    static Object lineOf(Map<String, Integer> lines, String pointer) {
        var p = pointer.startsWith("$") ? pointer.substring(1).replace('.', '/').replaceAll("\\[(\\d+)]", "/$1") : pointer;
        for (var q = p; ; q = q.substring(0, q.lastIndexOf('/'))) {
            if (lines.containsKey(q)) return lines.get(q);
            if (q.isEmpty() || q.lastIndexOf('/') < 0) return "?";
        }
    }
}
