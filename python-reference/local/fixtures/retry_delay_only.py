from brokoli import Pipeline, sink_file, source_file
from brokoli.ir import ir_digest, render_ir

with Pipeline("Slow Upstream", pipeline_id="slow-upstream") as p:
    rows = source_file("Read", path="/data/in.csv", format="csv", retry_delay=1500)
    rows >> sink_file("Write", path="/data/out.csv", format="csv", timeout=30)

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
