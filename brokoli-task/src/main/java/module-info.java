/**
 * The task API for JVM tasks run by Brokoli: {@link dev.brokoli.task.Task} marks a task method,
 * {@link dev.brokoli.task.Default} makes a task parameter optional, and
 * {@link dev.brokoli.task.Rows} and {@link dev.brokoli.task.Row} carry rows whose schema the
 * task does not declare. This module has no dependencies, because it is on the classpath of
 * every task. {@code dev.brokoli.task.internal} is exported only for the code the task processor
 * generates; it is not part of the API.
 */
module dev.brokoli.task {
    exports dev.brokoli.task;
    exports dev.brokoli.task.internal;
}
