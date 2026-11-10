import java.util.zip.GZIPInputStream

def installed = new File(localRepositoryPath, 'com/example/bundle-tasks/1.0')

def manifestOf = { String classifier ->
    def archive = new File(installed, "bundle-tasks-1.0-${classifier}.tar.gz")
    assert archive.isFile() : "${archive} was not installed"
    def tar = new String(new GZIPInputStream(new FileInputStream(archive)).readAllBytes(), 'ISO-8859-1')
    def start = tar.indexOf('{"format":"brokoli.task-bundle/v2"')
    assert start >= 0 : "${archive} has no manifest"
    tar.substring(start, tar.indexOf('\u0000', start))
}

def rollup = manifestOf('dailyRollup')
assert rollup.contains('"name":"Daily Rollup"')
assert rollup.contains('"build":{"builder":"brokoli-jvm/')
assert rollup.contains('"entrypoint":{"module":"com.example.tasks.Transforms_BrokoliTasks","symbol":"dailyRollup"}')
assert rollup.contains('{"path":"application.properties",')
assert rollup.contains('{"path":"com/example/tasks/Transforms_BrokoliTasks.class",')
assert rollup.contains('{"path":"lib/brokoli-task-')
assert !rollup.contains('META-INF/brokoli')

def passThrough = manifestOf('passThrough')
assert passThrough.contains('"name":"Pass Through"')
assert passThrough.contains('"symbol":"passThrough"')

def digest = { String manifest, String key -> (manifest =~ /"${key}":"(sha256:[0-9a-f]{64})"/)[0][1] }
assert digest(rollup, 'source_digest') == digest(passThrough, 'source_digest')
assert digest(rollup, 'interface_digest') != digest(passThrough, 'interface_digest')

def log = new File(basedir, 'build.log').text
assert log.contains('Task bundle Daily Rollup (com.example.tasks.Transforms_BrokoliTasks#dailyRollup): ')
assert log.contains('Task bundle Pass Through (com.example.tasks.Transforms_BrokoliTasks#passThrough): ')

true
