from brokoli import Pipeline, join, source_db
from brokoli.ir import ir_digest, render_ir

with Pipeline("Join Collision Policy", pipeline_id="join-collision-policy") as p:
    left = source_db("Left", conn_id="warehouse", query="SELECT id, name FROM left_table")
    right = source_db("Right", conn_id="warehouse", query="SELECT id, name FROM right_table")
    join("Merge", left=left, right=right, on="id", collision_policy="alias", right_alias="right_row")

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
