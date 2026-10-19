package stub;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Throwaway stub engine for spike 005. One process serves the engine's MCP endpoint (/mcp), the HTTP endpoints
 * that driver hooks call (/hooks/*), and an admin endpoint the experimenter uses to play the human and to switch
 * failure modes (/admin/*). Every request is appended to events.jsonl.
 *
 * The toy workflow is deliberately not SDLC (constitution Art. 3) and is all the driver learns from: the kit's
 * instructions are generic, and status carries the stage task, the permitted transitions, and any wait (Art. 9).
 */
public final class Main {

    static final ObjectMapper JSON = JsonMapper.builder().build();
    static final String TOKEN = env("ENGINE_TOKEN", "dev-token");
    static final int PORT = Integer.parseInt(env("ENGINE_PORT", "8787"));
    static final Path LOG = Path.of(env("ENGINE_LOG", "events.jsonl"));

    /** Failure modes the experimenter switches through /admin/mode?key=value. */
    static final Map<String, String> MODES = new ConcurrentHashMap<>(Map.of(
            "merge", "deny",       // allow | deny | hang | error: answer for `gh pr merge`
            "pretool", "allow",    // allow | hang | error: answer for every other tool call
            "stop", "engine",      // engine (stage task logic) | allow | block | error | hang
            "stop_limit", "4"));   // stage task iteration limit for the Stop hook

    // ---- toy workflow -------------------------------------------------------------------------------------

    record Stage(String id, String task, List<String> next, boolean humanGate, boolean terminal) {}

    static final Map<String, Stage> STAGES = new LinkedHashMap<>();
    static {
        STAGES.put("draft", new Stage("draft",
                "Write the file notes/haiku.md containing one haiku (three lines, 5-7-5 syllables) about the subject's"
                        + " topic. Then submit evidence `file` with the path and request the transition to `review`.",
                List.of("review"), false, false));
        STAGES.put("review", new Stage("review",
                "Add a first line `# <title>` to notes/haiku.md, choosing a title for the haiku. Then submit evidence"
                        + " `file` with the path and request the transition to `published`. A human must approve it.",
                List.of("published", "draft"), true, false));
        STAGES.put("published", new Stage("published", null, List.of(), false, true));
    }

    static final class Run {
        final String id;
        final String subject;
        String stage = "draft";
        String waitingFor;           // null, or the transition target waiting on a human
        int iterations;              // Stop hook continuations in the current stage visit
        boolean exhausted;
        final List<String> sessions = new ArrayList<>();
        final Map<String, Object> evidence = new LinkedHashMap<>();

        Run(String id, String subject) {
            this.id = id;
            this.subject = subject;
        }
    }

    static final Map<String, Run> RUNS = new ConcurrentHashMap<>();          // by subject
    static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();   // session id -> subject
    static final Map<String, Map<String, Object>> EVAL_TASKS = new ConcurrentHashMap<>();

    // ---- entry point --------------------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        var transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .contextExtractor(req -> McpTransportContext.create(Map.of("headers", headers(req))))
                .build();

        McpServer.sync(transport)
                .serverInfo("stub-engine", "0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools())
                .build();

        var ctx = new ServletContextHandler();
        ctx.addFilter(new FilterHolder(bearerFilter()), "/*", EnumSet.of(DispatcherType.REQUEST));
        ctx.addServlet(new ServletHolder(transport), "/mcp/*");
        ctx.addServlet(new ServletHolder(json(Main::hook)), "/hooks/*");
        ctx.addServlet(new ServletHolder(json(Main::admin)), "/admin/*");

        var server = new Server(PORT);
        server.setHandler(ctx);
        server.start();
        log("engine_started", Map.of("port", PORT));
        System.out.println("stub engine on http://localhost:" + PORT + " (token from ENGINE_TOKEN)");
        server.join();
    }

    /** Rejects any request without the driver token; /admin is open to the experimenter on localhost. */
    static Filter bearerFilter() {
        return (request, response, chain) -> {
            var req = (HttpServletRequest) request;
            var res = (HttpServletResponse) response;
            if (!req.getRequestURI().startsWith("/admin")
                    && !("Bearer " + TOKEN).equals(req.getHeader("Authorization"))) {
                log("rejected", Map.of("path", req.getRequestURI(), "method", req.getMethod(),
                        "authorization", String.valueOf(req.getHeader("Authorization") != null)));
                res.sendError(401, "bad or missing driver token");
                return;
            }
            if (req.getRequestURI().startsWith("/mcp")) {
                log("mcp_http", Map.of("method", req.getMethod(), "headers", headers(req)));
            }
            chain.doFilter(request, response);
        };
    }

    // ---- MCP tools ----------------------------------------------------------------------------------------

    static List<SyncToolSpecification> tools() {
        return List.of(
                tool("status", "The run for a subject: its stage, the stage task, the permitted transitions, and any"
                                + " wait. Call this first, and again whenever you are unsure what to do next.",
                        props("subject", "The subject key: the absolute path of the working directory."),
                        (ex, req) -> ok(status(arg(req, "subject")))),
                tool("start_run", "Start a run of the workflow on a subject, if none is active.",
                        props("subject", "The subject key: the absolute path of the working directory."),
                        (ex, req) -> {
                            var subject = arg(req, "subject");
                            var run = RUNS.computeIfAbsent(subject, s -> new Run("run-" + (RUNS.size() + 1), s));
                            SESSIONS.forEach((sid, subj) -> {
                                if (subj.equals(subject) && !run.sessions.contains(sid)) run.sessions.add(sid);
                            });
                            log("start_run", Map.of("subject", subject, "run", run.id, "mcp", ctx(ex)));
                            return ok(status(subject));
                        }),
                tool("submit_evidence", "Submit evidence for the current stage, for example {type: file, value: path}.",
                        props("subject", "The subject key.", "type", "Evidence type.", "value", "Evidence value."),
                        (ex, req) -> {
                            var run = RUNS.get(arg(req, "subject"));
                            if (run == null) return error("no active run for this subject");
                            run.evidence.put(run.stage + "." + arg(req, "type"), arg(req, "value"));
                            log("submit_evidence", Map.of("run", run.id, "stage", run.stage,
                                    "type", arg(req, "type"), "value", arg(req, "value")));
                            return ok(status(run.subject));
                        }),
                tool("request_transition", "Request the transition from the current stage to a permitted stage.",
                        props("subject", "The subject key.", "to", "The target stage."),
                        (ex, req) -> ok(transition(arg(req, "subject"), arg(req, "to")))),
                tool("get_evaluation_task", "Fetch an evaluation task packet by its ID.",
                        props("task_id", "The evaluation task ID."),
                        (ex, req) -> {
                            var task = EVAL_TASKS.get(arg(req, "task_id"));
                            log("get_evaluation_task", Map.of("task_id", arg(req, "task_id"), "found", task != null));
                            return task == null ? error("unknown task") : ok(task);
                        }),
                tool("submit_step_result", "Submit an evaluator's raw result for an evaluation task.",
                        props("task_id", "The evaluation task ID.", "raw_score", "The raw score.",
                                "rationale", "Why."),
                        (ex, req) -> {
                            log("submit_step_result", Map.of("task_id", arg(req, "task_id"),
                                    "raw_score", arg(req, "raw_score"), "rationale", arg(req, "rationale")));
                            return ok(Map.of("accepted", true));
                        }));
    }

    static Map<String, Object> status(String subject) {
        var run = subject == null ? null : RUNS.get(subject);
        var out = new LinkedHashMap<String, Object>();
        if (run == null) {
            out.put("run", null);
            out.put("next", "No active run for this subject. Call start_run if the user asked for work here.");
            return out;
        }
        var stage = STAGES.get(run.stage);
        out.put("run", run.id);
        out.put("stage", run.stage);
        out.put("terminal", stage.terminal());
        out.put("stage_task", stage.task());
        out.put("permitted_transitions", stage.next());
        out.put("evidence", run.evidence);
        out.put("iterations", run.iterations + "/" + MODES.get("stop_limit"));
        if (run.waitingFor != null) {
            out.put("waiting", "A human must approve the transition to `" + run.waitingFor + "` in the UI. Nothing"
                    + " to do until then; you may end the session. A later session resumes from status.");
        } else if (run.exhausted) {
            out.put("waiting", "The stage task's iteration limit is exhausted; the owner has been asked to decide.");
        } else if (stage.terminal()) {
            out.put("next", "The run is complete. Nothing to do.");
        } else {
            out.put("next", "Do the stage task, then request one of the permitted transitions.");
        }
        return out;
    }

    static Map<String, Object> transition(String subject, String to) {
        var run = RUNS.get(subject);
        if (run == null) return Map.of("accepted", false, "reason", "no active run for this subject");
        var stage = STAGES.get(run.stage);
        if (!stage.next().contains(to)) {
            log("transition_denied", Map.of("run", run.id, "from", run.stage, "to", to));
            return Map.of("accepted", false, "reason", "not permitted from " + run.stage, "status", status(subject));
        }
        if (!to.equals("draft") && !run.evidence.containsKey(run.stage + ".file")) {
            log("transition_denied", Map.of("run", run.id, "from", run.stage, "to", to, "why", "no file evidence"));
            return Map.of("accepted", false, "reason", "checkpoint failed: no `file` evidence for " + run.stage,
                    "status", status(subject));
        }
        if (stage.humanGate() && !to.equals("draft")) {
            run.waitingFor = to;
            log("waiting_for_human", Map.of("run", run.id, "from", run.stage, "to", to));
            return Map.of("accepted", true, "pending", "human approval", "status", status(subject));
        }
        move(run, to);
        return Map.of("accepted", true, "status", status(subject));
    }

    static void move(Run run, String to) {
        log("transition", Map.of("run", run.id, "from", run.stage, "to", to));
        run.stage = to;
        run.waitingFor = null;
        run.iterations = 0;
        run.exhausted = false;
    }

    // ---- hook endpoints -----------------------------------------------------------------------------------

    static Object hook(String name, JsonNode in, HttpServletResponse res) throws Exception {
        log("hook_" + name, Map.of("input", in));
        String sid = text(in, "session_id");
        String cwd = text(in, "cwd");
        switch (name) {
            case "session-start" -> {
                SESSIONS.put(sid, cwd);
                var run = RUNS.get(cwd);
                if (run != null && !run.sessions.contains(sid)) run.sessions.add(sid);
                var ctx = "Workflow engine status for this working directory (subject `" + cwd + "`), session `"
                        + sid + "` registered" + (run == null ? " unbound" : " with " + run.id) + ":\n"
                        + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(status(cwd));
                return Map.of("hookSpecificOutput",
                        Map.of("hookEventName", "SessionStart", "additionalContext", ctx));
            }
            case "pre-tool-use" -> {
                var command = in.path("tool_input").path("command").asString("");
                boolean merge = "Bash".equals(text(in, "tool_name")) && command.contains("gh pr merge");
                var mode = MODES.get(merge ? "merge" : "pretool");
                if (mode.equals("hang")) Thread.sleep(600_000);
                if (mode.equals("error")) {
                    res.setStatus(500);
                    return Map.of("error", "simulated engine error");
                }
                if (merge && !mode.equals("allow")) {
                    return Map.of("hookSpecificOutput", Map.of("hookEventName", "PreToolUse",
                            "permissionDecision", "deny",
                            "permissionDecisionReason", "Engine: the merge is not authorized for this run."));
                }
                return Map.of();
            }
            case "post-tool-use" -> {
                return Map.of();
            }
            case "stop" -> {
                return stop(cwd, res);
            }
            default -> {
                res.setStatus(404);
                return Map.of("error", "unknown hook " + name);
            }
        }
    }

    /** Stage task driven by a Stop hook: block (continue) until the stage moves, up to the iteration limit. */
    static Object stop(String cwd, HttpServletResponse res) throws Exception {
        switch (MODES.get("stop")) {
            case "allow" -> { return Map.of(); }
            case "block" -> { return Map.of("decision", "block", "reason", "Stub: keep going (mode=block)."); }
            case "error" -> { res.setStatus(500); return Map.of("error", "simulated"); }
            case "hang" -> { Thread.sleep(600_000); return Map.of(); }
            default -> { }
        }
        var run = RUNS.get(cwd);
        if (run == null || run.waitingFor != null || run.exhausted || STAGES.get(run.stage).terminal()) {
            log("stop_allowed", Map.of("subject", String.valueOf(cwd), "run", run == null ? "none" : run.id));
            return Map.of();
        }
        int limit = Integer.parseInt(MODES.get("stop_limit"));
        if (run.iterations >= limit) {
            run.exhausted = true;
            log("stage_task_exhausted", Map.of("run", run.id, "stage", run.stage, "iterations", run.iterations));
            return Map.of("systemMessage", "Stage task limit reached; the owner has been asked to decide.");
        }
        run.iterations++;
        log("stage_task_iteration", Map.of("run", run.id, "stage", run.stage, "iteration", run.iterations));
        return Map.of("decision", "block", "reason", "Engine: the stage task for `" + run.stage + "` is not done"
                + " (iteration " + run.iterations + " of " + limit + "). Call status, do the stage task, and"
                + " request a permitted transition.");
    }

    // ---- admin (the experimenter, standing in for the UI) -------------------------------------------------

    static Object admin(String name, JsonNode in, HttpServletResponse res) {
        switch (name) {
            case "mode" -> {
                in.properties().forEach(e -> MODES.put(e.getKey(), e.getValue().asString()));
                log("admin_mode", Map.of("modes", MODES));
                return MODES;
            }
            case "approve", "reject" -> {
                var run = RUNS.get(text(in, "subject"));
                if (run == null || run.waitingFor == null) return Map.of("error", "nothing waiting");
                log("human_" + name, Map.of("run", run.id, "to", run.waitingFor));
                if (name.equals("approve")) move(run, run.waitingFor);
                else move(run, "draft");
                return status(run.subject);
            }
            case "eval-task" -> {
                var id = "eval-" + (EVAL_TASKS.size() + 1);
                EVAL_TASKS.put(id, Map.of("task_id", id, "step", "haiku_quality",
                        "instructions", "Read notes/haiku.md and score it from 1 to 5 against the rubric.",
                        "rubric", List.of("1: not a haiku", "3: a haiku, but flat", "5: vivid and 5-7-5")));
                return Map.of("task_id", id);
            }
            case "runs" -> { return RUNS.values().stream().map(r -> Map.of("id", r.id, "subject", r.subject,
                    "stage", r.stage, "sessions", r.sessions, "iterations", r.iterations)).toList(); }
            case "reset" -> { RUNS.clear(); SESSIONS.clear(); return Map.of("reset", true); }
            default -> { res.setStatus(404); return Map.of("error", "unknown"); }
        }
    }

    // ---- plumbing -----------------------------------------------------------------------------------------

    interface JsonHandler { Object handle(String name, JsonNode in, HttpServletResponse res) throws Exception; }

    static HttpServlet json(JsonHandler handler) {
        return new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                var name = req.getPathInfo() == null ? "" : req.getPathInfo().substring(1);
                var body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode in = body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
                if (req.getQueryString() != null) {
                    var obj = (tools.jackson.databind.node.ObjectNode) in;
                    for (var kv : req.getQueryString().split("&")) {
                        var p = kv.split("=", 2);
                        obj.put(p[0], p.length > 1 ? java.net.URLDecoder.decode(p[1], StandardCharsets.UTF_8) : "");
                    }
                }
                Object out;
                try {
                    out = handler.handle(name, in, res);
                } catch (Exception e) {
                    res.setStatus(500);
                    out = Map.of("error", e.toString());
                }
                res.setContentType("application/json");
                res.getWriter().write(JSON.writeValueAsString(out));
            }
        };
    }

    static SyncToolSpecification tool(String name, String description, Map<String, Object> schema,
            BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(name).description(description).inputSchema(schema).build())
                .callHandler((ex, req) -> {
                    log("mcp_tool", Map.of("tool", name, "arguments", req.arguments(), "mcp", ctx(ex)));
                    return handler.apply(ex, req);
                })
                .build();
    }

    static Map<String, Object> props(String... nameThenDescription) {
        var properties = new LinkedHashMap<String, Object>();
        for (int i = 0; i < nameThenDescription.length; i += 2) {
            properties.put(nameThenDescription[i], Map.of("type", "string", "description", nameThenDescription[i + 1]));
        }
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()));
    }

    static Object ctx(McpSyncServerExchange ex) {
        var headers = ex.transportContext() == null ? null : ex.transportContext().get("headers");
        return Map.of("mcp_session", String.valueOf(ex.sessionId()), "headers", headers == null ? Map.of() : headers);
    }

    static Map<String, String> headers(HttpServletRequest req) {
        var out = new LinkedHashMap<String, String>();
        for (var name : Collections.list(req.getHeaderNames())) {
            out.put(name, name.equalsIgnoreCase("Authorization") ? "<redacted>" : req.getHeader(name));
        }
        return out;
    }

    static String arg(CallToolRequest req, String key) {
        var v = req.arguments() == null ? null : req.arguments().get(key);
        return v == null ? null : String.valueOf(v);
    }

    static String text(JsonNode n, String field) {
        return n.path(field).isMissingNode() ? null : n.path(field).asString();
    }

    static CallToolResult ok(Object value) {
        return CallToolResult.builder().addTextContent(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value))
                .build();
    }

    static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    static synchronized void log(String kind, Map<String, ?> data) {
        var line = new LinkedHashMap<String, Object>();
        line.put("ts", Instant.now().toString());
        line.put("kind", kind);
        line.putAll(data);
        try {
            Files.writeString(LOG, JSON.writeValueAsString(line) + "\n", StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("log failed: " + e);
        }
    }

    static String env(String key, String fallback) {
        var v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
