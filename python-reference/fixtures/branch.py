from brokoli import Pipeline, condition_node, notify, sink_file, source_file, transform, union
from brokoli.ir import ir_digest, render_ir

with Pipeline("Guarded Load", pipeline_id="guarded-load") as p:
    a = source_file("Read A", path="/data/a.csv", format="csv")
    b = source_file("Read B", path="/data/b.csv", format="csv")
    merged = union("Merge", a, b)
    gate = condition_node("Any Rows", expression="row_count > 0")
    merged >> gate
    # Deliberately NOT gate.when(transform(...) >> sink_file(...)): the
    # >> chain returns its right side, so when() would route the branch
    # to the sink and leave the transform with no input at all.
    shaped = transform("Shape", rules=[{"type": "rename", "mapping": {"x": "y"}}])
    gate.when(shaped)
    shaped >> sink_file("Load", path="/data/out.csv", format="csv")
    gate.otherwise(notify("Nothing", notify_type="webhook", webhook_url="https://hooks.example/x"))

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
