import dev.brokoli.pipeline.Pipeline
import dev.brokoli.pipeline.SourceFile
import dev.brokoli.pipeline.TaskProject
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

def classes = new File(basedir, 'target/classes')
def taskJars = new File(localRepositoryPath, 'dev/brokoli/brokoli-task').listFiles().findAll { it.isDirectory() }
        .collectMany { version -> version.listFiles().findAll { it.name == "brokoli-task-${version.name}.jar" } }
assert taskJars.size() == 1 : "expected one brokoli-task jar under ${localRepositoryPath}, found ${taskJars}"
def tasks = new URLClassLoader([classes.toURI().toURL()] as URL[], getClass().classLoader)

def readings = File.createTempFile('readings', '.json')
readings.deleteOnExit()
readings.text = JsonOutput.toJson([[sensor: 'north', count: 3], [sensor: 'south', count: 5]])

def built = Pipeline.builder("JVM SDK end to end ${UUID.randomUUID()}")
        .project(TaskProject.of(classes.toPath(), [taskJars[0].toPath()]))
        .build()
def scale = built.sourceFile('Read readings', SourceFile.options().path(readings.absolutePath).format('json'))
        .then(built.task(tasks.loadClass('com.example.readings.Readings'), 'scale'))

def pluginArchive = new File(basedir, 'target/run-on-core-1.0-scale.tar.gz').bytes
def pluginDigest = 'sha256:' + MessageDigest.getInstance('SHA-256').digest(pluginArchive).encodeHex()
assert built.taskBundles().size() == 1 : "the pipeline references ${built.taskBundles().size()} bundles, expected 1"
def archive = new ByteArrayOutputStream()
def digest = built.taskBundles()[0].writeTo(archive)
assert digest == pluginDigest : "the pipeline packaged scale as ${digest}, but the plugin's bundle is ${pluginDigest}"
def stored = send('POST', "/api/task-bundles-v2/${digest}",
        HttpRequest.BodyPublishers.ofByteArray(archive.toByteArray()), 'application/octet-stream')
assert stored.digest == digest

def pipeline = send('POST', '/api/pipelines', HttpRequest.BodyPublishers.ofString(built.toJson()), 'application/json')

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

def result = get("/api/runs/${run.id}/nodes/${scale.nodeId()}/preview")
assert result.rows == [[sensor: 'north', count: 9, unit: 'kWh'], [sensor: 'south', count: 15, unit: 'kWh']]

true
