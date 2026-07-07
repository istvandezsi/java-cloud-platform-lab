# Java Cloud Platform Lab

A small learning and reference project exploring how a Java application can be packaged, deployed, and operated using
modern cloud/platform engineering practices.

The goal is to connect my professional background in Java and enterprise software with my current focus on Kubernetes,
Terraform, AWS, CI/CD, observability, and infrastructure automation.

This is not intended to represent a production-ready platform. Instead, it is an incremental lab for documenting design
decisions, trade-offs, and practical implementation steps.

## Documentation

* [Operations notes](docs/operations.md) — health checks, logs, troubleshooting commands, Kubernetes probes, resource
  settings, and current operational scope.
* [Monitoring notes](docs/monitoring.md) — local Prometheus setup, Grafana provisioning, dashboard notes, and alert rule
  verification.
* [Architecture overview](docs/architecture.md) — high-level system overview, diagrams, and CI validation flow.

## Project scope

Current scope:

* Simple Java/Spring Boot application
* PostgreSQL-backed task API with validation and consistent JSON error responses
* Database schema migration with Flyway
* Simple browser-based task board UI
* Docker image
* Local Docker Compose runtime with PostgreSQL, Prometheus, and Grafana
* Kubernetes deployment manifests with external datasource configuration
* CI validation for application and platform configuration
* Basic observability with Actuator, Micrometer, Prometheus, Grafana, and alert rules
* Documentation of design decisions, trade-offs, and limitations

Planned future scope:

* OpenAPI documentation
* Terraform-managed AWS infrastructure
* AWS deployment experiments
* Kubernetes monitoring improvements

## Run locally

This project requires Java 21.

Run the tests:

```bash
./mvnw test
```

The application expects a PostgreSQL database when running outside the test profile. For a complete local runtime,
prefer Docker Compose.

Start the application directly only when a PostgreSQL database is available:

```bash
./mvnw spring-boot:run
```

Then verify the application using the commands in [Verify the application](#verify-the-application).

Stop the application with `Ctrl+C`.

## Run with Docker

Build the Docker image:

```bash
docker build -t java-cloud-platform-lab .
```

Run the container only when a PostgreSQL database is available through datasource environment variables:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/cloudlab \
  -e SPRING_DATASOURCE_USERNAME=cloudlab \
  -e SPRING_DATASOURCE_PASSWORD=cloudlab \
  --name java-cloud-platform-lab \
  java-cloud-platform-lab
```

For a complete local runtime, prefer Docker Compose.

Stop the container with `Ctrl+C`.

## Run with Docker Compose

Use Docker Compose to run the application together with PostgreSQL and the local monitoring stack:

```bash
docker compose up --build
```

The application is available at:

```text
http://localhost:8080
```

PostgreSQL is available locally at:

```text
localhost:5432
```

The default local database settings are:

```text
database: cloudlab
username: cloudlab
password: cloudlab
```

Prometheus is available at:

```text
http://localhost:9090
```

Grafana is available at:

```text
http://localhost:3000
```

The default local Grafana login is:

```text
admin / admin
```

Task data is stored in the Docker Compose PostgreSQL volume and survives application container restarts.

Stop the stack:

```bash
docker compose down
```

Remove the stack and delete the PostgreSQL data volume:

```bash
docker compose down -v
```

Use `docker compose down -v` only when you intentionally want to remove the local database data.

## Run with Kubernetes

This project includes Kubernetes manifests for the application deployment and service.

The Kubernetes manifests configure the application to connect to an externally provided PostgreSQL database. PostgreSQL
itself is not deployed by this project.

Before applying the manifests, review and adjust:

* `k8s/configmap.yaml` — datasource URL
* `k8s/secret.yaml` — datasource username and password

Build the Docker image first:

```bash
docker build -t java-cloud-platform-lab .
```

Apply the Kubernetes manifests:

```bash
kubectl apply -f k8s/
```

Check that the pod is running:

```bash
kubectl get pods
```

Forward the service port to your local machine:

```bash
kubectl port-forward service/java-cloud-platform-lab 8080:8080
```

Then verify the application using the commands in [Verify the application](#verify-the-application).

Stop port forwarding with `Ctrl+C`.

Remove the Kubernetes resources:

```bash
kubectl delete -f k8s/
```

## Verify the application

Verify the hello endpoint:

```bash
curl http://localhost:8080/api/hello
```

Expected response:

```json
{
  "message": "Hello from Java Cloud Platform Lab"
}
```

Verify the health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

## Browser UI

When the application is running, open:

```text
http://localhost:8080/
```

The browser UI provides a small task board backed by the Spring Boot task API.

From the browser, you can:

* create tasks
* list existing tasks
* update task titles
* mark tasks completed
* delete tasks

When running with Docker Compose, tasks are stored in PostgreSQL and survive application container restarts.

## Task API

The application includes a small task API backed by PostgreSQL.

Task titles are validated by the API. A task title must not be missing or blank. Validation failures return a JSON error
response.

List tasks:

```bash
curl http://localhost:8080/api/tasks
```

Create a task:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Try the task API"}'
```

Expected response:

```json
{
  "id": 1,
  "title": "Try the task API",
  "completed": false
}
```

Create a task with an invalid title:

```bash
curl -i -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"   "}'
```

Expected response:

```json
{
  "message": "Task title must not be blank"
}
```

Get a task by id:

```bash
curl http://localhost:8080/api/tasks/1
```

Update a task title:

```bash
curl -X PATCH http://localhost:8080/api/tasks/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated task title"}'
```

Update a task with an invalid title:

```bash
curl -i -X PATCH http://localhost:8080/api/tasks/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"   "}'
```

Expected response:

```json
{
  "message": "Task title must not be blank"
}
```

Mark a task completed:

```bash
curl -X PATCH http://localhost:8080/api/tasks/1/complete
```

Expected response:

```json
{
  "id": 1,
  "title": "Updated task title",
  "completed": true
}
```

Delete a task:

```bash
curl -X DELETE http://localhost:8080/api/tasks/1
```

When running with Docker Compose, task data is persisted in the PostgreSQL volume.

## License

This project is licensed under the MIT License.
