from brokoli import Pipeline, code, notify, sink_api, source_api
from brokoli.ir import ir_digest, render_ir

with Pipeline("Item Push", pipeline_id="item-push") as p:
    items = source_api(
        "Search",
        url="/items/search",
        method="POST",
        headers={"Accept": "application/json", "X-Api-Key": "${secret.items_key}"},
        body={"query": "{ items { id } }", "limit": 100, "nested": {"active": True, "tags": ["a", None]}},
        conn_id="items_api",
        params={"since": "${param.since}", "status": "active"},
        records="data.items",
        retries=2,
        retry_delay=1500,
        timeout=45,
    )
    shaped = code(
        "Shape",
        input=items,
        script="output_data = {'columns': columns, 'rows': rows}",
        python_path="/opt/venv/bin/python",
        timeout=120,
        max_memory_mb=512,
        max_cpu_seconds=60,
    )
    sink_api(
        "Push",
        input=shaped,
        url="https://sink.example.com/ingest",
        method="PUT",
        headers={"X-Source": "brokoli"},
        conn_id="sink_api",
        batch_size=250,
        auth_user="loader",
        auth_password="${secret.sink_password}",
        timeout=30,
        pool="api",
    )
    notify(
        "Alert",
        input=shaped,
        notify_type="slack",
        webhook_url="https://hooks.slack.com/services/T0/B0/x",
        message="Pipeline {{pipeline}} pushed {{rows}} rows",
        channel="#data-alerts",
        timeout=10,
    )

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
