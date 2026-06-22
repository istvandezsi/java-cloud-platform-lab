# Operations Notes

This document describes the basic operational behavior of the Java Cloud Platform Lab application.

The project is intentionally small and focused on local learning. Each operational capability is added incrementally so that the design decisions remain easy to understand.

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

At this stage, the health endpoint provides a basic application health signal. Future versions may expand this with additional health groups or checks for external dependencies.

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

Using the same endpoint for both readiness and liveness is acceptable for this small local setup. Future versions may separate readiness and liveness behavior so that startup readiness, dependency readiness, and process liveness can be evaluated differently.

## Logs

### Local Java run

When running the application locally:

```bash
./mvnw spring-boot:run
```

Logs are printed directly to the terminal.

### Docker

When running the application in Docker:

```bash
docker run --rm -p 8080:8080 --name java-cloud-platform-lab java-cloud-platform-lab
```

Logs are printed directly to the terminal.

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

Check whether Kubernetes resources exist:

```bash
kubectl get deployments
kubectl get pods
kubectl get services
```

Describe the pod if it is not running correctly:

```bash
kubectl describe pod <pod-name>
```

Check application logs:

```bash
kubectl logs <pod-name>
```

Forward the service port to the local machine:

```bash
kubectl port-forward service/java-cloud-platform-lab 8080:8080
```

Then verify the application:

```bash
curl http://localhost:8080/api/hello
curl http://localhost:8080/actuator/health
```

Remove the Kubernetes resources:

```bash
kubectl delete -f k8s/
```

## Current scope and future improvements

This project is currently focused on a small, local learning setup for running and operating a Java application with Docker and Kubernetes.

The current setup includes:

* A Spring Boot application with a basic Actuator health endpoint
* A Docker image for local containerized execution
* Kubernetes Deployment and Service manifests
* Basic readiness and liveness probes
* Local verification using `curl`, Docker, and `kubectl`

Future improvements may include:

* Resource requests and limits for Kubernetes workloads
* Separate readiness and liveness health groups
* Additional Actuator endpoints for metrics
* Prometheus and Grafana integration
* CI checks for Docker image builds
* More structured logging
* Cloud infrastructure using Terraform and AWS
* A more production-like deployment model

These improvements are intentionally left for later milestones so that each layer of the project can be added, tested, and documented incrementally.
