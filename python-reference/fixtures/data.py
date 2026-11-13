from brokoli import Pipeline, join, quality_check, sink_db, source_db
from brokoli.ir import ir_digest, render_ir

with Pipeline("Warehouse Load", pipeline_id="warehouse-load") as p:
    orders = source_db("Orders", conn_id="prod_pg", query="SELECT * FROM orders")
    users = source_db("Users", conn_id="prod_pg", query="SELECT id, email FROM users")
    joined = join("Enrich", left=orders, right=users, on="user_id")
    gated = joined >> quality_check("Gate", rules=[
        {"rule": "not_null", "column": "email", "on_failure": "block"},
    ])
    gated >> sink_db("Load", conn_id="warehouse", table="orders_enriched",
                     mode="upsert", key_columns=["id"])

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
