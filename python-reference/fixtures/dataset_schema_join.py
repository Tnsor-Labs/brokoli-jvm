from brokoli import Pipeline, dataset_schema, join, source_api
from brokoli.ir import ir_digest, render_ir

with Pipeline("Dataset Schema Join", pipeline_id="dataset-schema-join") as p:
    left = source_api(
        "Left",
        url="https://example.test/left",
        schema=dataset_schema({"id": {"kind": "int64"}, "name": {"kind": "string"}}, "closed"),
    )
    right = source_api(
        "Right",
        url="https://example.test/right",
        schema=dataset_schema({"id": {"kind": "int64"}, "name": {"kind": "string"}}, "closed"),
    )
    join("Merge", left, right, on="id", collision_policy="alias", right_alias="right_row")

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
