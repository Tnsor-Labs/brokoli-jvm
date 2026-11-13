from brokoli import Pipeline, code, notify, sink_api, sink_db, sink_file, source_api, source_db, source_file
from brokoli.ir import ir_digest, render_ir


with Pipeline("IO options", pipeline_id="io-options") as pipeline:
    file = source_file("Read file", path="/data/in.csv", retries=3, timeout=60, pool="files")
    api = source_api("Fetch", url="https://api.example.com/graphql", source_type="graphql", pool="api")
    db = source_db("Query", query="SELECT 1", conn_id="warehouse", pool="warehouse")
    shaped = code(
        "Shape",
        input=file,
        language="typescript",
        script="export default (rows) => rows",
        node_path="/usr/bin/node",
        max_memory_mb=256,
        max_cpu_seconds=30,
        retries=1,
        retry_delay=2000,
        pool="compute",
    )
    sink_db(
        "Load",
        input=shaped,
        table="events",
        conn_id="clickhouse",
        create_table=True,
        table_engine="ReplacingMergeTree",
        retries=2,
        timeout=300,
        pool="clickhouse",
    )
    sink_file("Save", input=api, path="/data/out.csv", pool="files")
    sink_api("Post", input=db, url="https://hooks.example.com/x", retries=1, pool="api")
    notify("Tell", input=db, webhook_url="https://hooks.example.com/notify", retries=2, retry_delay=1000)


payload = pipeline.to_json()
print(render_ir(payload), end="")
print("DIGEST", ir_digest(payload))
