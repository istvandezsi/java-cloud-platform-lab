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

```powershell
.\mvnw.cmd test
```

Start the application:

```powershell
.\mvnw.cmd spring-boot:run
```

Verify the hello endpoint:

```text
http://localhost:8080/api/hello
```

Expected response:

```json
{
  "message": "Hello from Java Cloud Platform Lab"
}
```

Verify the health endpoint:

```text
http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

Stop the application with `Ctrl+C`.

## License

This project is licensed under the MIT License.