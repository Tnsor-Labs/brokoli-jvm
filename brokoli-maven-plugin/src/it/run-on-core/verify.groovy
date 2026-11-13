import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

def server = System.getenv('BROKOLI_E2E_SERVER')
def apiKey = System.getenv('BROKOLI_E2E_API_KEY')
assert server : 'BROKOLI_E2E_SERVER is not set: run-on-core needs a running core server'
assert apiKey : 'BROKOLI_E2E_API_KEY is not set: run-on-core needs the core server\'s API key'

def http = HttpClient.newHttpClient()
def json = new JsonSlurper()

def send = { String method, String path, HttpRequest.BodyPublisher body, String contentType ->
    def request = HttpRequest.newBuilder(URI.create(server + path))
            .header('Authorization', "Bearer ${apiKey}")
            .header('Content-Type', contentType)
            .method(method, body)
            .build()
    def response = http.send(request, HttpResponse.BodyHandlers.ofString())
    assert response.statusCode() in 200..299 : "${method} ${path} returned ${response.statusCode()}: ${response.body()}"
    json.parseText(response.body())
}
def get = { String path -> send('GET', path, HttpRequest.BodyPublishers.noBody(), 'application/json') }
def post = { String path, Object body ->
    send('POST', path, HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(body)), 'application/json')
}

def archive = new File(basedir, 'target/run-on-core-1.0-scale.tar.gz').bytes
def digest = 'sha256:' + MessageDigest.getInstance('SHA-256').digest(archive).encodeHex()
def stored = send('POST', "/api/task-bundles-v2/${digest}", HttpRequest.BodyPublishers.ofByteArray(archive),
        'application/octet-stream')
assert stored.digest == digest

def taskFile = json.parse(new File(basedir, 'target/classes/META-INF/brokoli/tasks/com.example.readings.Readings.json'))
def task = taskFile.tasks.find { it.method == 'scale' }
assert task : "the task file has no task scale: ${taskFile}"

def readings = File.createTempFile('readings', '.json')
readings.deleteOnExit()
readings.text = JsonOutput.toJson([[sensor: 'north', count: 3], [sensor: 'south', count: 5]])

def pipeline = post('/api/pipelines', [
        name      : "JVM SDK end to end ${UUID.randomUUID()}",
        parameters: task.parameters,
        nodes     : [
                [id    : 'read', type: 'source_file', name: 'Read readings',
                 config: [path: readings.absolutePath, format: 'json']],
                [id    : 'scale', type: 'task', name: task.name,
                 config: [task_bundle: [digest: digest, format: 'brokoli.task-bundle/v2']], interface: task.interface],
        ],
        edges     : [[from: 'read', to: 'scale']],
])

def run = post("/api/pipelines/${pipeline.id}/run", [parameters: [factor: 3]])
def finished = ['success', 'failed', 'cancelled']
def waitSeconds = 120
def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitSeconds)
def detail = get("/api/runs/${run.id}")
while (!(detail.status in finished) && System.nanoTime() < deadline) {
    sleep 500
    detail = get("/api/runs/${run.id}")
}
assert detail.status in finished : "run ${run.id} did not finish within ${waitSeconds} s, its last status was ${detail.status}"
assert detail.status == 'success' : "run ${run.id} ended ${detail.status}: ${detail.error}"
assert detail.parameters == [factor: 3]

def result = get("/api/runs/${run.id}/nodes/scale/preview")
assert result.rows == [[sensor: 'north', count: 9, unit: 'kWh'], [sensor: 'south', count: 15, unit: 'kWh']]

true
