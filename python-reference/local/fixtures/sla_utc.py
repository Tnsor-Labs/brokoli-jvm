from brokoli import Pipeline, sink_file, source_file
from brokoli.ir import ir_digest, render_ir

with Pipeline(
    "Morning Report",
    pipeline_id="morning-report",
    schedule="0 6 * * *",
    schedule_timezone="UTC",
    sla="06:05",
) as p:
    rows = source_file("Read", path="/data/in.csv", format="csv")
    rows >> sink_file("Write", path="/data/out.csv", format="csv")

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
