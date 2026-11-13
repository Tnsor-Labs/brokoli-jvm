from brokoli import Pipeline, sink_file, source_file
from brokoli.ir import ir_digest, render_ir

with Pipeline(
    "Hourly Slices",
    pipeline_id="hourly-slices",
    schedule="0 * * * *",
    schedule_timezone="Africa/Maputo",
    catch_up=True,
    sla="07:30 Africa/Maputo",
    depends_on=["upstream-etl"],
    tags=["intervals"],
    webhook=True,
    on_failure="https://hooks.example/oncall",
) as p:
    rows = source_file("Read", path="/data/in.csv", format="csv")
    rows >> sink_file("Slice", path="/data/slice-${interval.start}.csv", format="csv")

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
