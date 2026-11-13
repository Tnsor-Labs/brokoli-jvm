from brokoli import Pipeline, bash, source_file
from brokoli.ir import ir_digest, render_ir


with Pipeline("Bash export", pipeline_id="bash-export") as pipeline:
    source = source_file("Input", path="/data/in.csv")
    bash(
        "Refresh",
        input=source,
        command='./refresh.sh --since "$SINCE"',
        working_dir="/data/jobs",
        env={"SINCE": "${param.since}"},
        timeout=600,
        retries=2,
        retry_delay=30000,
        max_memory_mb=512,
        max_cpu_seconds=300,
        node_key="refresh-export",
    )


payload = pipeline.to_json()
print(render_ir(payload), end="")
print("DIGEST", ir_digest(payload))
