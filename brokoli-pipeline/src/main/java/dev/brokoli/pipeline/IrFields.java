package dev.brokoli.pipeline;

final class IrFields {

    static final String PIPELINE_ID = "pipeline_id";
    static final String NAME = "name";
    static final String DESCRIPTION = "description";
    static final String SCHEDULE = "schedule";
    static final String ENABLED = "enabled";
    static final String IR_VERSION = "ir_version";
    static final String NODES = "nodes";
    static final String EDGES = "edges";
    static final String TAGS = "tags";
    static final String DEPENDS_ON = "depends_on";
    static final String DEPENDENCY_RULES = "dependency_rules";
    static final String PARAMETERS = "parameters";
    static final String PARAMS = "params";
    static final String HOOKS = "hooks";
    static final String WEBHOOK_URL = "webhook_url";
    static final String WEBHOOK_TOKEN = "webhook_token";
    static final String SCHEDULE_TIMEZONE = "schedule_timezone";
    static final String CATCHUP = "catchup";
    static final String SLA_DEADLINE = "sla_deadline";
    static final String SLA_TIMEZONE = "sla_timezone";
    static final String ON_START = "on_start";
    static final String ON_SUCCESS = "on_success";
    static final String ON_FAILURE = "on_failure";
    static final String URL = "url";
    static final String EXTRA = "extra";

    static final String ID = "id";
    static final String TYPE = "type";
    static final String CONFIG = "config";
    static final String CAPABILITIES = "capabilities";
    static final String INTERFACE = "interface";
    static final String POSITION = "position";

    static final String FROM = "from";
    static final String TO = "to";

    static final String PATH = "path";
    static final String FORMAT = "format";
    static final String RULES = "rules";
    static final String TASK_BUNDLE = "task_bundle";
    static final String DIGEST = "digest";
    static final String CONN_ID = "conn_id";
    static final String COMPRESS = "compress";
    static final String COMMAND = "command";
    static final String WORKING_DIR = "working_dir";
    static final String ENV = "env";
    static final String MAX_MEMORY_MB = "max_memory_mb";
    static final String MAX_CPU_SECONDS = "max_cpu_seconds";
    static final String MAX_RETRIES = "max_retries";
    static final String RETRY_BACKOFF = "retry_backoff";
    static final String RETRY_DELAY = "retry_delay";
    static final String TIMEOUT = "timeout";
    static final String POOL = "pool";
    static final String QUERY = "query";
    static final String URI = "uri";
    static final String SCHEMA_HINT = "_schema_hint";
    static final String QUERY_RESULT = "query_result";
    static final String TABLE = "table";
    static final String MODE = "mode";
    static final String KEY_COLUMNS = "key_columns";
    static final String TRUNCATE = "truncate";
    static final String CREATE_TABLE = "create_table";
    static final String TABLE_ENGINE = "table_engine";
    static final String JOIN_TYPE = "join_type";
    static final String LEFT_KEY = "left_key";
    static final String RIGHT_KEY = "right_key";
    static final String COLLISION_POLICY = "collision_policy";
    static final String RIGHT_ALIAS = "right_alias";

    static final String SOURCE_FILE = "source_file";
    static final String SOURCE_DB = "source_db";
    static final String SINK_DB = "sink_db";
    static final String JOIN = "join";
    static final String QUALITY_CHECK = "quality_check";
    static final String TRANSFORM = "transform";
    static final String SINK_FILE = "sink_file";
    static final String BASH = "bash";
    static final String TASK = "task";

    private IrFields() {
    }
}
