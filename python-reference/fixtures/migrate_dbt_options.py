from brokoli import Pipeline, dbt, migrate
from brokoli.ir import ir_digest, render_ir


with Pipeline("Migrate and dbt", pipeline_id="migrate-dbt") as pipeline:
    copied = migrate(
        "Copy events",
        source_conn_id="oltp",
        target_conn_id="dwh",
        query="SELECT * FROM events",
        table="events",
        mode="upsert",
        key_columns=["id", "updated_at"],
        dialect="postgres",
        chunk_size=1000,
        create_table=False,
        retries=2,
        retry_delay=5000,
        timeout=900,
        pool="dwh",
        partition={
            "column": "updated_at",
            "strategy": "date",
            "boundaries": ["2026-01-01T00:00:00.000Z", "2026-02-01T00:00:00Z", "2026-03-01T00:00:00Z"],
            "max_parallel": 2,
        },
    )
    dbt(
        "Models",
        command="build",
        select="marts",
        vars={"run_date": "2026-10-14", "full_refresh": True, "nested": {"b": 2, "a": [1, "x"]}},
        conn_id="dwh",
        target_schema="analytics",
        output_model="orders",
        input=copied,
        retries=1,
        pool="dwh",
    )


payload = pipeline.to_json()
print(render_ir(payload), end="")
print("DIGEST", ir_digest(payload))
