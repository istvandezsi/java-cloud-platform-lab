# Java Cloud Platform Lab

A small learning and reference project exploring how a Java application can be packaged, deployed, and operated using modern cloud/platform engineering practices.

The goal is to connect my professional background in Java and enterprise software with my current focus on Kubernetes, Terraform, AWS, CI/CD, observability, and infrastructure automation.

This is not intended to represent a production-ready platform. Instead, it is an incremental lab for documenting design decisions, trade-offs, and practical implementation steps.

## Planned scope

- Simple Java application
- Docker image
- Kubernetes deployment manifests
- Terraform-managed infrastructure
- CI/CD pipeline
- Basic observability with metrics and dashboards
- Documentation of design decisions and limitations

## Run locally

This project requires Java 21.

Run the tests:

```bash
./mvnw test
```

Start the application:

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

Run the container:

```bash
docker run --rm -p 8080:8080 --name java-cloud-platform-lab java-cloud-platform-lab
```

Then verify the application using the commands in [Verify the application](#verify-the-application).

Stop the container with `Ctrl+C`.

## Run with Kubernetes

This project can be run in a local Kubernetes cluster, such as Docker Desktop Kubernetes.

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

## License

This project is licensed under the MIT License.