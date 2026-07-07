# Operations Notes

This document describes the basic operational behavior of the Java Cloud Platform Lab application.

The project is intentionally small and focused on local learning. Each operational capability is added incrementally so
that the design decisions remain easy to understand.

## Health endpoint

The application exposes a Spring Boot Actuator health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

This endpoint is useful for checking whether the application is running and able to respond to HTTP requests.

The application also depends on a configured PostgreSQL database. When the database is unavailable, application startup
or readiness may fail depending on when the connection is needed.

## Metrics endpoint

The application exposes a Prometheus metrics endpoint through Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/prometheus
```

This endpoint returns metrics in Prometheus text format.

Example metric groups include:

- Application startup and readiness timing
- HTTP server request metrics
- JDBC connection pool metrics
- JVM information
- JVM memory and buffer metrics
- Disk space metrics
- Executor/thread pool metrics

The Docker Compose setup runs Prometheus and Grafana locally. Prometheus scrapes the application metrics endpoint, and
Grafana displays a provisioned dashboard.

## Database and migrations

The task API stores task data in PostgreSQL.

The application uses Flyway to apply database schema migrations on startup. Migration files are stored in:

```text
src/main/resources/db/migration
```

The current schema contains a `tasks` table for task title and completion state.

When running with Docker Compose, PostgreSQL data is stored in a named Docker volume. This means task data survives
application container restarts.

Stop the Docker Compose stack without deleting database data:

```bash
docker compose down
```

Stop the stack and delete the PostgreSQL data volume:

```bash
docker compose down -v
```

Use `docker compose down -v` only when you intentionally want to remove local database data.

## Docker Compose operations

Start the full local stack:

```bash
docker compose up --build
```

The stack includes:

- Spring Boot application
- PostgreSQL
- Prometheus
- Grafana

The application is available at:

```text
http://localhost:8080
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

View logs for the application:

```bash
docker compose logs app
```

View logs for PostgreSQL:

```bash
docker compose logs db
```

View logs for Prometheus:

```bash
docker compose logs prometheus
```

View logs for Grafana:

```bash
docker compose logs grafana
```

Restart only the application container:

```bash
docker compose restart app
```

This is useful for verifying that task data survives application restarts.

Check the generated Docker Compose configuration:

```bash
docker compose config
```

## Kubernetes health checks

The Kubernetes Deployment uses the Actuator health endpoint for both readiness and liveness checks.

Readiness probe:

* Tells Kubernetes whether the application is ready to receive traffic
* Uses `/actuator/health`
* Starts checking after a short initial delay

Liveness probe:

* Tells Kubernetes whether the application should be considered alive
* Uses `/actuator/health`
* Starts checking after a longer initial delay
* Allows Kubernetes to restart the container if the probe fails repeatedly

Using the same endpoint for both readiness and liveness is acceptable for this small local setup. Future versions may
separate readiness and liveness behavior so that startup readiness, dependency readiness, and process liveness can be
evaluated differently.

The current Kubernetes manifests do not deploy PostgreSQL. A database must be provided separately through the application
datasource configuration. The datasource URL is configured through a ConfigMap, while the datasource username and
password are configured through a Secret.

## Kubernetes resource requests and limits

The Kubernetes Deployment defines basic CPU and memory requests and limits for the application container.

Requests describe the amount of CPU and memory Kubernetes should reserve when scheduling the pod:

```yaml
requests:
  cpu: "100m"
  memory: "256Mi"
```

Limits describe the maximum CPU and memory the container is allowed to use:

```yaml
limits:
  cpu: "500m"
  memory: "512Mi"
```

For this project, these values are simple local-development defaults. They are not based on production load testing or
capacity planning.

Adding requests and limits makes the Kubernetes configuration more explicit and avoids running the pod as a best-effort
workload.

## Logs

### Local Java run

When running the application locally:

```bash
./mvnw spring-boot:run
```

Logs are printed directly to the terminal.

The application expects a PostgreSQL database to be available through the configured datasource settings.

### Docker Compose

When running the local stack with Docker Compose, logs can be viewed per service:

```bash
docker compose logs app
docker compose logs db
docker compose logs prometheus
docker compose logs grafana
```

### Docker

When running only the application container:

```bash
docker run --rm -p 8080:8080 --name java-cloud-platform-lab java-cloud-platform-lab
```

Logs are printed directly to the terminal.

The standalone application container expects PostgreSQL to be available through datasource environment variables. For
the complete local runtime, prefer Docker Compose.

If the container is running in the background, logs can be viewed with:

```bash
docker logs java-cloud-platform-lab
```

### Kubernetes

When running the application in Kubernetes, first find the pod:

```bash
kubectl get pods
```

Then view logs:

```bash
kubectl logs <pod-name>
```

Example:

```bash
kubectl logs java-cloud-platform-lab-xxxxx
```

## Basic troubleshooting

Check whether the application responds:

```bash
curl http://localhost:8080/api/hello
curl http://localhost:8080/actuator/health
```

Check whether the task API responds:

```bash
curl http://localhost:8080/api/tasks
```

Check Docker Compose service status:

```bash
docker compose ps
```

Check application logs:

```bash
docker compose logs app
```

Check PostgreSQL logs:

```bash
docker compose logs db
```

Check Prometheus logs:

```bash
docker compose logs prometheus
```

Check Grafana logs:

```bash
docker compose logs grafana
```

Restart only the application container:

```bash
docker compose restart app
```

Check whether Kubernetes resources exist:

```bash
kubectl get deployments
kubectl get pods
kubectl get services
```

Check whether the datasource ConfigMap and Secret exist:

```bash
kubectl get configmap java-cloud-platform-lab-datasource-config
kubectl get secret java-cloud-platform-lab-datasource-secret
```

Describe the pod if it is not running correctly:

```bash
kubectl describe pod <pod-name>
```

Check Kubernetes application logs:

```bash
kubectl logs <pod-name>
```

Forward the service port to the local machine:

```bash
kubectl port-forward service/java-cloud-platform-lab 8080:8080
```

Remove the Kubernetes resources:

```bash
kubectl delete -f k8s/
```

## Current scope and future improvements

This project is currently focused on a small, local learning setup for running and operating a Java application with
Docker, Docker Compose, Kubernetes manifests, PostgreSQL persistence, and basic observability.

The current setup includes:

* A Spring Boot application with Actuator health and metrics endpoints
* PostgreSQL-backed task persistence
* Flyway database migrations
* A Docker image for local containerized execution
* A Docker Compose runtime with PostgreSQL, Prometheus, and Grafana
* Kubernetes Deployment and Service manifests
* External datasource configuration through Kubernetes ConfigMap and Secret
* Basic readiness and liveness probes
* Local verification using `curl`, Docker, Docker Compose, and `kubectl`
* Prometheus-format metrics exposed through Spring Boot Actuator
* Local Prometheus scraping and Grafana dashboard provisioning

Future improvements may include:

* More production-like resource sizing based on actual measurements
* Separate readiness and liveness health groups
* More structured logging
* Kubernetes-based monitoring resources
* ServiceMonitor configuration
* More application-specific metrics
* Managed database configuration for cloud deployments
* Cloud infrastructure using Terraform and AWS
* A more production-like deployment model

These improvements are intentionally left for later milestones so that each layer of the project can be added, tested,
and documented incrementally.
