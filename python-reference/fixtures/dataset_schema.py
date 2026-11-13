from brokoli import Pipeline, dataset_schema, source_api
from brokoli.ir import ir_digest, render_ir

with Pipeline("Dataset Schema", pipeline_id="dataset-schema") as p:
    source_api(
        "Fetch",
        url="https://example.test/data",
        schema=dataset_schema(
            {"id": {"kind": "int64"}, "name": {"kind": "string", "nullable": True}},
            additional_columns="closed",
        ),
    )

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
