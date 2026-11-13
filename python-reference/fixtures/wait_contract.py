from brokoli import Pipeline, contract_gate, source_file, wait
from brokoli.ir import ir_digest, render_ir


with Pipeline("Wait and contract", pipeline_id="wait-contract") as pipeline:
    ready = wait(
        "Export landed",
        condition="file_exists",
        path="/data/exports/*.csv",
        poll_interval=300,
        timeout="4h",
        offset=0.5,
        node_key="export-landed",
    )
    source = source_file("Read export", path="/data/exports/orders.csv")
    ready >> source
    passed = wait("Upstream done", input=source, condition="pipeline", pipeline_id="nightly")
    contract_gate(
        "Orders contract",
        input=passed,
        contract={
            "ir_version": "1.0",
            "contract": {"id": "orders", "version": "1"},
            "input": {"kind": "record-stream"},
            "rules": [
                {
                    "id": "email-valid",
                    "kind": "record",
                    "path": "$.email",
                    "predicate": {"op": "format", "format": "email"},
                    "on_breach": {"severity": "error", "action": "quarantine"},
                }
            ],
        },
        retries=1,
    )


payload = pipeline.to_json()
print(render_ir(payload), end="")
print("DIGEST", ir_digest(payload))
