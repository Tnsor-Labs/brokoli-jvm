from brokoli import Param, Secret, Pipeline, sink_api, source_api
from brokoli.ir import ir_digest, render_ir
from brokoli.pagination import offset_pages

with Pipeline("Item Sync", pipeline_id="item-sync") as p:
    items = source_api(
        "Fetch",
        url="https://api.example.com/items",
        headers={"X-Api-Key": Secret("items_key")},
        params={"since": Param("since"), "status": "active"},
        records="data.items",
        timeout=30,
        retries=3,
        pagination=offset_pages(page_size=100).with_execution(page_max_retries=3),
    )
    items >> sink_api("Push", url="https://sink.example.com/ingest", method="POST")

print(render_ir(p.to_json()), end="")
print("DIGEST " + ir_digest(p.to_json()))
