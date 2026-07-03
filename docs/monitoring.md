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

- Application startup and readiness timing
- HTTP server request metrics
- JVM information
- JVM memory and buffer metrics
- Disk space metrics
- Executor/thread pool metrics

## Application-specific metrics

The application exposes a custom counter for calls to the hello endpoint.

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

- Application up status
- HTTP requests per second
- Hello requests per second
- JVM memory used
- Application startup time

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

- Kubernetes-based Prometheus deployment
- ServiceMonitor configuration
- More application-specific custom metrics