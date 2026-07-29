# Monitoring

The local Docker Compose environment runs Prometheus and Grafana alongside the application and PostgreSQL.
Prometheus scrapes Spring Boot Actuator metrics, and Grafana uses Prometheus as its provisioned data source.

## Metrics endpoint

The application exposes Prometheus text format at:

```bash
curl http://localhost:8080/actuator/prometheus
```

Available metrics include JVM, HTTP server, JDBC connection pool, disk, executor, startup, and application-specific
task API measurements.

## Task API metric

Task activity is recorded by:

```text
cloudlab_task_api_operations_total
```

The counter has two low-cardinality labels:

| Label | Values |
|---|---|
| `operation` | `list`, `get`, `create`, `update`, `complete`, `delete` |
| `outcome` | `success`, `not_found`, `validation_error` |

`validation_error` applies to create and update requests; `not_found` applies to operations that address a task by ID.
Task IDs, titles, exception messages, and other user-provided values are not used as labels.

Generate representative outcomes:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Verify task metrics"}'

curl http://localhost:8080/api/tasks/999999

curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"   "}'
```

The resulting series include:

```text
cloudlab_task_api_operations_total{operation="create",outcome="success"}
cloudlab_task_api_operations_total{operation="create",outcome="validation_error"}
cloudlab_task_api_operations_total{operation="get",outcome="not_found"}
```

Useful PromQL queries:

```promql
cloudlab_task_api_operations_total
```

```promql
cloudlab_task_api_operations_total{operation="create",outcome="success"}
```

```promql
rate(cloudlab_task_api_operations_total[5m])
```

## Prometheus

Start the local stack:

```bash
docker compose up --build
```

Prometheus is available at `http://localhost:9090`. Its configuration is stored in
`prometheus/prometheus.yml`, and it scrapes `app:8080/actuator/prometheus` over the Compose network.

Verify target health with:

```promql
up{job="java-cloud-platform-lab"}
```

A healthy target returns a value of `1` for instance `app:8080`.

Verify application data with:

```promql
http_server_requests_seconds_count{job="java-cloud-platform-lab"}
```

```promql
cloudlab_task_api_operations_total{job="java-cloud-platform-lab"}
```

## Alert rule

Prometheus loads `prometheus/alerts.yml`, which defines:

```promql
up{job="java-cloud-platform-lab"} == 0
```

Open `http://localhost:9090/alerts` and confirm that `ApplicationDown` is present. It remains inactive while Prometheus
can scrape the application, enters pending when the target is unavailable, and fires after 30 seconds.

The repository defines the rule but does not configure Alertmanager or notification delivery.

## Grafana

Grafana is available at `http://localhost:3000` with the local credentials `admin / admin`.

The provisioned **Java Cloud Platform Lab** dashboard contains:

- application availability;
- aggregate HTTP request rate;
- JVM memory use;
- application startup time;
- successful task-operation rates grouped by operation;
- unsuccessful task-operation rates grouped by operation and outcome.

Generate activity for the rate panels:

```bash
for i in {1..5}; do
  curl -s http://localhost:8080/api/tasks > /dev/null
done

for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/tasks \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Grafana task $i\"}" > /dev/null
done

curl -s http://localhost:8080/api/tasks/999999 > /dev/null

curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"   "}' > /dev/null
```

Allow at least two 15-second Prometheus scrapes before evaluating a new five-minute rate series.

Grafana provisioning is maintained in:

- `grafana/dashboards/java-cloud-platform-lab.json`;
- `grafana/provisioning/datasources/prometheus.yaml`;
- `grafana/provisioning/dashboards/dashboards.yaml`.

Prometheus and Grafana are local-only in this repository. They are not deployed by the Kubernetes manifests or
Terraform configuration.
