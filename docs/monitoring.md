# Monitoring Notes

This document describes the local monitoring setup for the Java Cloud Platform Lab application.

The project exposes Prometheus-format metrics through Spring Boot Actuator and can run a local Prometheus server using
Docker Compose.

## Metrics endpoint

The application exposes metrics at:

```bash
curl http://localhost:8080/actuator/prometheus
```

This endpoint returns metrics in Prometheus text format.

Example metric groups include:

* Application startup and readiness timing
* HTTP server request metrics
* JDBC connection pool metrics
* JVM information
* JVM memory and buffer metrics
* Disk space metrics
* Executor/thread pool metrics
* Application-specific API metrics

## Application-specific metrics

The application exposes custom counters for the hello endpoint and task API operations.

### Hello endpoint metric

Call the endpoint:

```bash
curl http://localhost:8080/api/hello
```

Then check the Prometheus metrics endpoint:

```bash
curl http://localhost:8080/actuator/prometheus
```

The custom counter should appear as:

```text
hello_requests_total
```

In Prometheus, the metric can be queried with:

```text
hello_requests_total
```

### Task API operation metric

Task API activity is recorded with the following metric:

```text
cloudlab_task_api_operations_total
```

The metric uses two low-cardinality labels:

* `operation` identifies the task operation
* `outcome` identifies the result

Supported operation values are:

* `list`
* `get`
* `create`
* `update`
* `complete`
* `delete`

Supported outcome values are:

* `success`
* `not_found`
* `validation_error`

The metric does not include task IDs, task titles, exception messages, or other user-provided values.

Create a task:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Verify task metrics"}'
```

Request a task that does not exist:

```bash
curl http://localhost:8080/api/tasks/999999
```

Submit an invalid task:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"   "}'
```

Then check the metrics endpoint:

```bash
curl http://localhost:8080/actuator/prometheus
```

Example output:

```text
cloudlab_task_api_operations_total{operation="create",outcome="success"} 1.0
cloudlab_task_api_operations_total{operation="create",outcome="validation_error"} 1.0
cloudlab_task_api_operations_total{operation="get",outcome="not_found"} 1.0
```

In Prometheus, all task API operations can be queried with:

```text
cloudlab_task_api_operations_total
```

Successful task creation operations can be queried with:

```text
cloudlab_task_api_operations_total{operation="create",outcome="success"}
```

Task API operation rates can be queried with:

```text
rate(cloudlab_task_api_operations_total[5m])
```

## Local Prometheus setup

The local monitoring setup uses Docker Compose to run both the application and Prometheus.

Start the application and Prometheus:

```bash
docker compose up --build
```

The application is available at:

```text
http://localhost:8080
```

Prometheus is available at:

```text
http://localhost:9090
```

## Prometheus scrape configuration

Prometheus is configured in:

```text
prometheus/prometheus.yml
```

The scrape target is:

```text
app:8080
```

This works because both services run inside the same Docker Compose network. Prometheus reaches the application by its
Compose service name, not by `localhost`.

## Verify the application

Check the application health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

Check the metrics endpoint:

```bash
curl http://localhost:8080/actuator/prometheus
```

## Verify Prometheus scraping

Open Prometheus:

```text
http://localhost:9090
```

Run this query:

```text
up
```

Expected result:

```text
up{instance="app:8080", job="java-cloud-platform-lab"} 1
```

Then query an application metric:

```text
http_server_requests_seconds_count
```

This should return HTTP request metrics scraped from the Spring Boot application.

Query the custom task API metric:

```text
cloudlab_task_api_operations_total
```

This should return task API operation counters after one or more task endpoints have been called.

## Verify Prometheus alert rule

Prometheus is configured to load local alerting rules from:

```text
prometheus/alerts.yml
```

The local setup includes an `ApplicationDown` alert based on the application scrape status:

```text
up{job="java-cloud-platform-lab"} == 0
```

To verify the alert rule locally, start the monitoring stack:

```bash
docker compose up --build
```

Then open the Prometheus alerts page:

```text
http://localhost:9090/alerts
```

Confirm that the `ApplicationDown` alert is visible.

When the application is running and Prometheus can scrape it successfully, the alert should remain inactive.

This local setup defines alerting rules only. Notification delivery through Alertmanager, email, or Slack is out of
scope.

## Verify Grafana dashboard

Open Grafana:

```text
http://localhost:3000
```

The default local login is:

```text
admin / admin
```

Grafana is configured with Prometheus as its default data source.

Open the dashboard:

```text
Java Cloud Platform Lab
```

The dashboard includes basic panels for:

* Application up status
* HTTP requests per second
* Hello requests per second
* JVM memory used
* Application startup time

The task API metric is not included in the current dashboard yet. Adding task API panels is planned as a separate
follow-up change.

The dashboard is provisioned from:

```text
grafana/dashboards/java-cloud-platform-lab.json
```

The Prometheus data source is provisioned from:

```text
grafana/provisioning/datasources/prometheus.yaml
```

The dashboard provider is configured in:

```text
grafana/provisioning/dashboards/dashboards.yaml
```

## Stop the local monitoring setup

Stop the running containers:

```bash
docker compose down
```

## Current scope and future improvements

The current setup runs Prometheus and Grafana locally. Prometheus scrapes application metrics from the Spring Boot
Actuator Prometheus endpoint, and Grafana visualizes a small set of application metrics.

Future improvements may include:

* Adding task API metrics to the Grafana dashboard
* Kubernetes-based Prometheus deployment
* ServiceMonitor configuration
* Additional application-specific metrics
