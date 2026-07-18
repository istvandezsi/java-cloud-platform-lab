# Operations

This document is the practical runbook for running, validating, troubleshooting, and cleaning up Java Cloud Platform Lab.

It intentionally avoids repeating:

- architecture and design rationale from [Architecture](architecture.md);
- Prometheus queries and Grafana details from [Monitoring](monitoring.md);
- Terraform resource descriptions, variables, backend design, and limitations from the
  [Terraform documentation](../terraform/README.md).

## Safety conventions

Commands are marked where additional care is required:

- **AWS credentials required** — contacts an AWS account.
- **May incur AWS costs** — operates on deployed cloud resources.
- **Destructive** — deletes data or resources.
- **Validation only** — checks configuration without deploying anything.

A controlled AWS live-verification exercise has been completed successfully. The environment was removed after
verification and is not kept running.

Use [AWS Live Verification](aws-live-verification.md) for the complete deploy–verify–destroy procedure. Routine
validation in this document does not apply infrastructure.

## Prerequisites

The complete toolset consists of:

- Git
- Java 21
- Docker with Docker Compose
- Terraform 1.15 or a compatible 1.x release
- AWS CLI for AWS-specific operations
- `kubectl` for Kubernetes operations
- a Bash-compatible shell

Verify the main tools:

```bash
git --version
java -version
docker version
docker compose version
terraform version
aws --version
kubectl version --client
```

The repository includes the Maven wrapper, so a separate Maven installation is not required.

## Repository setup

Clone and enter the repository:

```bash
git clone https://github.com/istvandezsi/java-cloud-platform-lab.git
cd java-cloud-platform-lab
```

Before starting issue work:

```bash
git switch main
git pull --ff-only
git switch -c <issue-branch>
```

Check the working tree:

```bash
git status
```

## Run tests

Docker must be available because the test suite includes a PostgreSQL Testcontainers integration test.

```bash
./mvnw test
```

A successful run ends with:

```text
BUILD SUCCESS
```

## Run the local Docker Compose environment

Docker Compose is the preferred complete local runtime.

Start the application, PostgreSQL, Prometheus, and Grafana:

```bash
docker compose up --build
```

Run the stack in the background:

```bash
docker compose up -d --build
```

Check service status:

```bash
docker compose ps
```

Local endpoints:

| Component | Address |
|---|---|
| Application and task board | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |
| PostgreSQL | `localhost:5432` |

Local development credentials:

```text
PostgreSQL database: cloudlab
PostgreSQL username: cloudlab
PostgreSQL password: cloudlab

Grafana username: admin
Grafana password: admin
```

These credentials are local development defaults only.

## Verify the application

Verify the hello endpoint:

```bash
curl http://localhost:8080/api/hello
```

Verify general health:

```bash
curl http://localhost:8080/actuator/health
```

Verify readiness:

```bash
curl http://localhost:8080/actuator/health/readiness
```

Verify liveness:

```bash
curl http://localhost:8080/actuator/health/liveness
```

Verify the task API:

```bash
curl http://localhost:8080/api/tasks
```

Create a task:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Verify the task API"}'
```

The complete API contract is available through Swagger UI rather than duplicated in this runbook.

## Verify PostgreSQL persistence

Create a task, restart only the application container, and then list tasks again:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Persistence check"}'

docker compose restart app
```

Wait until the application becomes ready:

```bash
until curl -fsS http://localhost:8080/actuator/health/readiness > /dev/null; do
  sleep 2
done
```

List tasks:

```bash
curl http://localhost:8080/api/tasks
```

The task should remain because PostgreSQL data is stored in the named Docker volume.

## Docker Compose logs and cleanup

Follow application logs:

```bash
docker compose logs --follow app
```

View service-specific logs:

```bash
docker compose logs app
docker compose logs db
docker compose logs prometheus
docker compose logs grafana
```

Validate the Compose configuration:

```bash
docker compose config
```

Stop the stack while preserving PostgreSQL data:

```bash
docker compose down
```

### Delete local database data

**Destructive**

```bash
docker compose down -v
```

This removes the PostgreSQL volume and all locally stored tasks.

## Monitoring

Verify that the application exposes Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Use the dedicated [Monitoring documentation](monitoring.md) for:

- application-specific metric names;
- Prometheus queries;
- scrape verification;
- alert-rule verification;
- Grafana dashboard contents.

## Validate Prometheus configuration

**Validation only**

Run the same checks used by CI.

Validate the Prometheus configuration:

```bash
docker run --rm \
  --entrypoint promtool \
  -v "$PWD/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "$PWD/prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro" \
  prom/prometheus:latest \
  check config /etc/prometheus/prometheus.yml
```

Validate alert rules:

```bash
docker run --rm \
  --entrypoint promtool \
  -v "$PWD/prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro" \
  prom/prometheus:latest \
  check rules /etc/prometheus/alerts.yml
```

## Validate Kubernetes manifests

**Validation only**

This command validates the files under `k8s/` without connecting to or modifying a Kubernetes cluster:

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  ghcr.io/yannh/kubeconform:latest \
  -strict \
  -summary \
  /workspace/k8s
```

It checks Kubernetes schemas, field names, structure, and value types.

It does not verify runtime networking, database availability, or credentials.

## Run the application with Kubernetes

The Kubernetes manifests deploy only the application. PostgreSQL must be supplied separately.

Review before applying:

```text
k8s/configmap.yaml
k8s/secret.yaml
```

For local Docker Desktop Kubernetes testing, start the Compose database:

```bash
docker compose up -d db
```

Temporarily set the ConfigMap datasource URL to:

```text
jdbc:postgresql://host.docker.internal:5432/cloudlab
```

Build the local image:

```bash
docker build -t java-cloud-platform-lab:latest .
```

Confirm the active cluster:

```bash
kubectl config current-context
```

Apply the manifests:

```bash
kubectl apply -f k8s/
```

Wait for the rollout:

```bash
kubectl rollout status deployment/java-cloud-platform-lab
```

Inspect resources:

```bash
kubectl get deployments
kubectl get pods
kubectl get services
```

Forward the application service:

```bash
kubectl port-forward service/java-cloud-platform-lab 8081:8080
```

Verify through the forwarded port:

```bash
curl http://localhost:8081/actuator/health/readiness
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/api/tasks
```

Stop port forwarding with `Ctrl+C`.

Before committing, restore the generic ConfigMap datasource URL:

```text
jdbc:postgresql://postgres.example.local:5432/cloudlab
```

Verify that no local-only manifest change remains:

```bash
git diff -- k8s/configmap.yaml
```

### Remove Kubernetes resources

**Destructive**

Confirm the active context:

```bash
kubectl config current-context
```

Remove the project resources:

```bash
kubectl delete -f k8s/
```

## Kubernetes troubleshooting

Check rollout and pod state:

```bash
kubectl rollout status deployment/java-cloud-platform-lab
kubectl get pods
```

Describe a failing pod:

```bash
kubectl describe pod <pod-name>
```

Read application logs:

```bash
kubectl logs <pod-name>
```

Follow logs:

```bash
kubectl logs --follow <pod-name>
```

Check the service and its endpoints:

```bash
kubectl get service java-cloud-platform-lab
kubectl get endpoints java-cloud-platform-lab
```

Check datasource resources without printing secret values:

```bash
kubectl get configmap java-cloud-platform-lab-datasource-config
kubectl get secret java-cloud-platform-lab-datasource-secret
```

Common failures include:

- unreachable PostgreSQL;
- invalid datasource credentials;
- an unavailable or stale local image;
- Flyway migration failure;
- readiness checks failing during startup.

## Validate Terraform locally

The Terraform root module is documented in detail in
[terraform/README.md](../terraform/README.md).

Copy the example variables:

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars
```

The local file is ignored by Git and must not contain credentials or passwords.

Check formatting:

```bash
terraform -chdir=terraform fmt -check -recursive
```

Initialize without activating the partial S3 backend:

```bash
terraform -chdir=terraform init \
  -backend=false \
  -input=false \
  -lockfile=readonly
```

Validate:

```bash
terraform -chdir=terraform validate -no-color
```

These commands do not create AWS infrastructure.

## Run an AWS-backed speculative plan

**AWS credentials required**

A speculative plan contacts AWS but does not create or modify resources.

Set the AWS CLI profile and the same region configured by the Terraform `aws_region` variable:

```bash
export AWS_PROFILE=<aws-profile>
export AWS_REGION=eu-central-1
```

Replace `eu-central-1` when Terraform uses another region.

Verify the selected identity and region:

```bash
aws sts get-caller-identity
echo "$AWS_REGION"
```

Ensure `terraform/terraform.tfvars` contains a nonblank image tag. For plan-only validation, a placeholder is sufficient:

```hcl
application_image_tag = "replace-with-published-image-tag"
```

Run:

```bash
terraform -chdir=terraform plan \
  -input=false \
  -lock-timeout=30s
```

Review the proposed resource changes carefully.

Do not run `terraform apply` as part of routine validation or documentation review.

## Publish an immutable image to ECR

These commands require an existing Terraform-managed ECR repository.

**AWS credentials required**

**May incur AWS costs**

Set the profile and region:

```bash
export AWS_PROFILE=<aws-profile>
export AWS_REGION=eu-central-1
```

Read the repository URL:

```bash
ECR_REPOSITORY_URL=$(terraform -chdir=terraform output -raw ecr_repository_url)
ECR_REGISTRY=${ECR_REPOSITORY_URL%%/*}
```

Authenticate Docker:

```bash
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login \
      --username AWS \
      --password-stdin "$ECR_REGISTRY"
```

Use the current commit as the immutable tag:

```bash
IMAGE_TAG=$(git rev-parse --short HEAD)
```

Build, tag, and push:

```bash
docker build \
  --tag "java-cloud-platform-lab:$IMAGE_TAG" \
  .

docker tag \
  "java-cloud-platform-lab:$IMAGE_TAG" \
  "$ECR_REPOSITORY_URL:$IMAGE_TAG"

docker push "$ECR_REPOSITORY_URL:$IMAGE_TAG"
```

Set the same tag in `terraform/terraform.tfvars`:

```hcl
application_image_tag = "<git-commit-sha>"
```

The complete first-deployment bootstrap sequence and its rationale remain authoritative in the
[Terraform documentation](../terraform/README.md).

## Inspect a deployed AWS environment

These commands apply only after a future controlled deployment.

**AWS credentials required**

**May incur AWS costs**

Retrieve the public URL:

```bash
APPLICATION_URL=$(terraform -chdir=terraform output -raw application_url)
```

Verify the application:

```bash
curl "$APPLICATION_URL/api/hello"
curl "$APPLICATION_URL/actuator/health"
curl "$APPLICATION_URL/actuator/health/readiness"
curl "$APPLICATION_URL/api/tasks"
```

Retrieve ECS names:

```bash
ECS_CLUSTER_NAME=$(terraform -chdir=terraform output -raw ecs_cluster_name)
ECS_SERVICE_NAME=$(terraform -chdir=terraform output -raw ecs_service_name)
```

Inspect the ECS service:

```bash
aws ecs describe-services \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER_NAME" \
  --services "$ECS_SERVICE_NAME" \
  --query 'services[0].{desired:desiredCount,running:runningCount,pending:pendingCount,status:status}' \
  --output table
```

## Inspect CloudWatch logs

**AWS credentials required**

Read the log-group name:

```bash
APPLICATION_LOG_GROUP=$(terraform -chdir=terraform output -raw application_log_group_name)
```

Show recent application logs:

```bash
aws logs tail "$APPLICATION_LOG_GROUP" \
  --region "$AWS_REGION" \
  --since 30m
```

Follow logs:

```bash
aws logs tail "$APPLICATION_LOG_GROUP" \
  --region "$AWS_REGION" \
  --follow
```

Use application logs to diagnose datasource configuration, Flyway migrations, PostgreSQL connectivity, and Spring Boot
startup.

## Redeploy after database-secret rotation

ECS reads the RDS-managed secret when a task starts. Running tasks do not automatically receive rotated credentials.

**AWS credentials required**

**May incur AWS costs**

Force a replacement deployment:

```bash
aws ecs update-service \
  --region "$AWS_REGION" \
  --cluster "$(terraform -chdir=terraform output -raw ecs_cluster_name)" \
  --service "$(terraform -chdir=terraform output -raw ecs_service_name)" \
  --force-new-deployment
```

Wait for service stability:

```bash
aws ecs wait services-stable \
  --region "$AWS_REGION" \
  --cluster "$(terraform -chdir=terraform output -raw ecs_cluster_name)" \
  --services "$(terraform -chdir=terraform output -raw ecs_service_name)"
```

## AWS cleanup precautions

Terraform-managed AWS resources can incur costs while they exist.

A live-verification exercise must reserve enough time to deploy, verify, troubleshoot, and remove the environment
in one session. Follow the dedicated [AWS Live Verification](aws-live-verification.md) runbook.

Before any destructive operation, confirm:

```bash
aws sts get-caller-identity
echo "$AWS_REGION"
terraform -chdir=terraform workspace show
```

Review the destruction plan:

```bash
terraform -chdir=terraform plan -destroy
```

The actual destroy command is intentionally not part of the normal validation workflow.

The lab database is disposable and is configured without a final snapshot during destruction.

## General troubleshooting sequence

Check the repository first:

```bash
git status
git diff --check
```

Run tests:

```bash
./mvnw test
```

Validate local configuration:

```bash
docker compose config
terraform -chdir=terraform fmt -check -recursive
terraform -chdir=terraform validate -no-color
```

Check local service state:

```bash
docker compose ps
```

Check application health:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/health/liveness
```

Inspect application and database logs:

```bash
docker compose logs app
docker compose logs db
```

Verify PostgreSQL health:

```bash
docker compose exec db pg_isready -U cloudlab -d cloudlab
```

For AWS failures, inspect:

- AWS identity and region;
- Terraform plan output;
- ECS service events;
- stopped-task reasons;
- CloudWatch application logs;
- load-balancer target health.

## Pre-commit validation

Check whitespace and the working tree:

```bash
git diff --check
git status --short
```

Run the primary repository checks:

```bash
./mvnw test
docker build -t java-cloud-platform-lab .
docker compose config
terraform -chdir=terraform fmt -check -recursive
terraform -chdir=terraform init \
  -backend=false \
  -input=false \
  -lockfile=readonly
terraform -chdir=terraform validate -no-color
```

Use the Kubernetes and Prometheus validation commands above when their related files change.

No infrastructure should be applied as part of documentation validation.
