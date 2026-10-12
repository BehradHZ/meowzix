## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Token-efficient agent tooling

- Use Graphify for repository-wide architecture, dependencies, impact analysis, and cross-file relationships. Query the persistent graph; update it incrementally after repository changes.
- Use RTK for supported shell, Git, search, test, build, lint, package-manager, log, listing, and diff commands. Preserve raw output when compression would hide required evidence.
- Use Context Mode for large tool results, web/API responses, logs, datasets, and multi-file processing. Keep bulk data outside model context when its indexed tools can answer the question.
- Apply Ponytail before implementation: reuse existing code, then standard/native features, then installed dependencies, and make the smallest correct change. Never weaken correctness, security, validation, error handling, accessibility, requirements, or tests.
- Apply Caveman to user-facing prose: concise, exact commands, paths, errors, warnings, security details, and technical facts. Do not compress away debugging evidence.
- Route only to tools needed for the task. Correctness outranks token reduction; reuse gathered evidence and avoid rereading unchanged files.

@RTK.md
