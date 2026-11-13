from brokoli import Map, Pipeline, code, source_api, source_db
from brokoli.ir import ir_digest, render_ir


with Pipeline("Node map", pipeline_id="node-map") as pipeline:
    customers = source_db("Customers", query="SELECT id, region FROM customers", conn_id="shop")
    regions = source_db("Regions", query="SELECT region FROM regions", conn_id="shop")
    source_db(
        "Orders",
        query="SELECT * FROM orders WHERE customer_id = ${item.id}",
        conn_id="shop",
        map=Map(over=customers, key="id", max_items=500, max_parallel=8, on_item_failure="continue"),
    )
    source_api("Profile", url="https://api.example.com/customers/${item.id}", map=Map(over=customers))
    code(
        "Pair",
        input=customers,
        script="output_data = {'columns': columns, 'rows': rows}",
        map=Map(over={"customer": customers, "region": regions}, on_item_failure="skip"),
    )


payload = pipeline.to_json()
print(render_ir(payload), end="")
print("DIGEST", ir_digest(payload))
