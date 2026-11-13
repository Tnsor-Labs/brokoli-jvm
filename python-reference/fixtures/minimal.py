from brokoli import Pipeline, sink_file, source_file, transform
from brokoli.ir import ir_digest, render_ir

with Pipeline("Sales ETL", pipeline_id="sales-etl", description="Clean the daily sales file", tags=["finance", "daily"]) as p:
    rows = source_file("Read", path="/data/sales.csv", format="csv")
    clean = rows >> transform("Clean", rules=[
        {"type": "rename", "mapping": {"amt": "amount"}},
        {"type": "filter", "condition": "amount > 0"},
    ])
    clean >> sink_file("Write", path="/data/out.csv", format="csv")

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
