# Throwaway runner-evaluator image for spike 005: Claude Code headless on Linux, non-root.
FROM node:24-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates jq && rm -rf /var/lib/apt/lists/*
RUN npm install -g @anthropic-ai/claude-code@2.1.302 && npm cache clean --force
RUN useradd -m evaluator
COPY result.schema.json prompt.md evaluate.sh /eval/
USER evaluator
WORKDIR /home/evaluator
ENTRYPOINT ["/eval/evaluate.sh"]
