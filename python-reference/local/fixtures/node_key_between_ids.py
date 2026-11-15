from brokoli import Pipeline, sink_file, source_file
from brokoli.ir import ir_digest, render_ir

with Pipeline("Three Reads", pipeline_id="three-reads") as p:
    first = source_file("Read", path="/data/a.csv", format="csv")
    keyed = source_file("Read", path="/data/b.csv", format="csv", node_key="read_2")
    last = source_file("Read", path="/data/c.csv", format="csv")
    first >> sink_file("Write", path="/data/a-out.csv", format="csv")
    keyed >> sink_file("Write", path="/data/b-out.csv", format="csv")
    last >> sink_file("Write", path="/data/c-out.csv", format="csv")

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
