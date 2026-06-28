# Monitoring Notes

This document describes the local monitoring setup for the Java Cloud Platform Lab application.

The project exposes Prometheus-format metrics through Spring Boot Actuator and can run a local Prometheus server using Docker Compose.

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

This works because both services run inside the same Docker Compose network. Prometheus reaches the application by its Compose service name, not by `localhost`.

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

## Stop the local monitoring setup

Stop the running containers:

```bash
docker compose down
```

## Current scope and future improvements

The current setup runs Prometheus locally and scrapes application metrics from the Spring Boot Actuator Prometheus endpoint.

Future improvements may include:

- Grafana dashboards
- Alerting rules
- Kubernetes-based Prometheus deployment
- ServiceMonitor configuration
- More application-specific custom metrics