# Terraform

This directory contains the Terraform root module for the Java Cloud Platform Lab AWS infrastructure.

The current configuration establishes the Terraform and AWS provider requirements, shared input variables, resource
naming conventions, common tags, a partial Amazon S3 backend declaration, the foundational VPC network, an Amazon ECR
repository for application images, a private Amazon RDS PostgreSQL database, an Amazon ECS Fargate application service
with CloudWatch logging, and an internet-facing Application Load Balancer providing public HTTP access.

## Prerequisites

Install:

* Terraform 1.15 or a later compatible 1.x release
* AWS CLI for AWS authentication and resource operations
* Docker for building and publishing the application image

Confirm the Terraform installation:

```bash
terraform version
```

Confirm the AWS CLI installation:

```bash
aws --version
```

Confirm the Docker installation:

```bash
docker version
```

## Local configuration

Copy the example variable file:

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars
```

Edit `terraform/terraform.tfvars` when different local values are required.

The example configuration includes values for:

* the AWS region
* the deployment environment
* the project name
* the immutable application image tag
* the VPC CIDR
* the PostgreSQL database name
* the PostgreSQL master username
* the RDS instance class

Set `application_image_tag` to the tag of the application image that should be deployed. In an existing environment, the
image must already be published to the Terraform-managed ECR repository before the ECS service uses it. A brand-new
environment requires the bootstrap sequence described in the container image registry section.

The local `terraform.tfvars` file is ignored by Git and must not contain committed credentials or secrets.

A database password is not accepted as a Terraform input. RDS generates and manages the master password through AWS
Secrets Manager.

## AWS credentials

Do not add AWS access keys, secret keys, or session tokens to Terraform configuration, variable files, or backend
configuration files.

The AWS provider and S3 backend can obtain credentials through standard AWS credential sources, including:

* environment variables
* the shared AWS credentials and configuration files
* container credentials
* an attached IAM role

For a locally configured AWS CLI profile, Terraform can use the profile selected through the `AWS_PROFILE` environment
variable.

Formatting, initialization without the backend, and validation do not require access to an AWS account. AWS
authentication is required when configuring the S3 backend or running operations such as `terraform plan` and
`terraform apply`.

## Network architecture

The root module defines one IPv4 VPC across two Availability Zones in the configured AWS region.

The network contains:

* two public subnets, one in each selected Availability Zone
* two private subnets, one in each selected Availability Zone
* one internet gateway
* one public route table with a default route through the internet gateway
* one private route table without an internet or NAT route
* one internet-facing Application Load Balancer across both public subnets

For the example VPC CIDR of `10.0.0.0/16`, Terraform derives these subnet ranges:

| Subnet           | CIDR           |
|------------------|----------------|
| Public subnet 1  | `10.0.0.0/24`  |
| Public subnet 2  | `10.0.1.0/24`  |
| Private subnet 1 | `10.0.10.0/24` |
| Private subnet 2 | `10.0.11.0/24` |

The public subnets have a route to the internet gateway. Automatic public IPv4 assignment is disabled, so resources must
request a public address explicitly when required.

The private subnets currently have no route outside the VPC. NAT or private service endpoints can be introduced later
when workloads require outbound connectivity from private subnets.

The ECS application tasks run in the public subnets and explicitly request public IPv4 addresses. This provides outbound
connectivity for pulling the private ECR image, retrieving the RDS-managed secret, and publishing logs to CloudWatch.

Public application requests enter through the Application Load Balancer. The ECS task public addresses do not provide
direct application access because the application security group accepts port `8080` only from the load-balancer
security group.

The VPC, subnets, internet gateway, route tables, load balancer, target group, and security groups inherit the
provider-level common tags and receive descriptive `Name` tags where supported.

## Container image registry

The root module defines one private Amazon ECR repository for the application container image.

The repository enables scan-on-push, immutable tags, and forced deletion during lab teardown. Use a unique tag such as
the Git commit SHA; CI does not publish images or authenticate to AWS.

`application_image_tag` is combined with the repository URL in the ECS task definition:

```text
<ecr-repository-url>:<application-image-tag>
```

The ECR repository and ECS service belong to the same root module, so a new environment requires a two-stage bootstrap:

1. create the repository with a temporary nonblank image tag;
2. publish the real image with an immutable tag;
3. update `application_image_tag`;
4. update the ECS task definition and service.

The temporary ECS deployment cannot start until the real image is published. Image publishing commands are maintained
in [Operations](../docs/operations.md#publish-an-immutable-image-to-ecr); the tested end-to-end procedure is recorded in
[AWS Live Verification](../docs/aws-live-verification.md).

## PostgreSQL database

The root module defines one private Amazon RDS PostgreSQL instance for the application persistence layer.

The database configuration includes:

* PostgreSQL with no explicitly pinned engine version
* an instance class supplied through `database_instance_class`
* 20 GiB of General Purpose SSD storage using `gp3`
* encrypted storage
* a single-AZ deployment
* no public accessibility
* no deletion protection
* no automated backup retention
* no final snapshot during deletion

These settings favor a small, disposable learning environment rather than a production database.

### Database topology

The RDS DB subnet group contains both existing private subnets across two Availability Zones.

```mermaid
flowchart LR
    Internet[Internet]

    subgraph VPC[VPC]
        subgraph Public[Public subnets in two Availability Zones]
            LoadBalancer[Application Load Balancer]
            Application[ECS Fargate task]
        end

        subgraph Private[Private DB subnets in two Availability Zones]
            Database[(Single-AZ RDS PostgreSQL)]
        end
    end

    Internet -->|TCP 80| LoadBalancer
    LoadBalancer -->|security group: TCP 8080| Application
    Application -->|security group: TCP 5432| Database
```

The subnet group spans two Availability Zones so RDS has valid private placement options. The current database is
single-AZ, so the instance itself runs in one Availability Zone rather than maintaining a standby instance in the other
Availability Zone.

The database is assigned only the dedicated database security group and is not publicly accessible.

### Database credentials

RDS manages the master password through AWS Secrets Manager.

Terraform supplies the configured master username but does not supply, expose, or commit a database password. The
generated secret contains the master credentials and is identified through the `database_master_secret_arn` output.

The ECS task execution role can retrieve only this RDS-managed secret. The task definition injects:

* `SPRING_DATASOURCE_USERNAME` from the secret's `username` JSON field
* `SPRING_DATASOURCE_PASSWORD` from the secret's `password` JSON field

The secret value is not read into a Terraform output.

### Credential rotation

RDS rotates the managed master-user secret every seven days by default. ECS resolves secret values only when a task
starts, so the service needs a replacement task after rotation. Automatic replacement is not configured; the command is
documented in [Operations](../docs/operations.md#redeploy-after-database-secret-rotation).

A production implementation should automate credential refresh and use a dedicated least-privilege application
database user rather than the master user.

### Database access

The database security group accepts PostgreSQL connections on TCP port `5432` only from the ECS application security
group.

The application security group allows outbound traffic required for AWS service access and the database connection. Its
only inbound rule accepts TCP port `8080` from the load-balancer security group.

No public, internet-wide, VPC-wide, or subnet-wide database ingress rule is defined.

### Database lifecycle

The database is configured for straightforward lab teardown:

* automated backup retention is disabled
* `deletion_protection` is disabled
* `skip_final_snapshot` is enabled

Destroying the environment therefore removes the database without creating a final snapshot. Database data should be
treated as disposable, and this lifecycle configuration should not be reused for a production database.

## ECS Fargate application runtime

The root module defines one ECS cluster and one ECS Fargate service for the Spring Boot application.

The task configuration uses:

* Fargate launch type
* `awsvpc` network mode
* Fargate platform version `1.4.0`
* 256 CPU units
* 512 MiB memory
* one essential application container
* container port `8080`
* one desired running task
* a 120-second load-balancer health-check grace period

The task definition uses the Terraform-managed ECR repository and the configured immutable application image tag.

### Application configuration

The datasource URL is constructed from the Terraform-managed RDS resource:

```text
jdbc:postgresql://<database-address>:<database-port>/<database-name>
```

The task definition provides the URL through `SPRING_DATASOURCE_URL` and injects the database username and password from
the RDS-managed Secrets Manager secret.

### IAM

The ECS task execution role trusts the ECS tasks service principal.

The role receives:

* the AWS-managed `AmazonECSTaskExecutionRolePolicy`
* a narrowly scoped inline policy allowing `secretsmanager:GetSecretValue` only for the RDS-managed master secret

The managed execution policy supports pulling the private ECR image and publishing container logs through the `awslogs`
log driver.

A separate application task role is not defined because the application does not currently call AWS APIs directly.

### Application networking

The ECS service runs tasks across the existing public subnets and assigns each task a public IPv4 address.

This networking choice is used because the private subnets do not currently have a NAT
gateway or VPC endpoints. Without one of those outbound paths, tasks in the private subnets could not retrieve the ECR
image, database secret, or CloudWatch Logs service endpoints.

The ECS service registers the container named `application` and port `8080` with the Application Load Balancer target
group.

The application security group accepts port `8080` only from the load-balancer security group. Consequently:

* internet clients reach the application only through the Application Load Balancer
* the application cannot be reached directly through the task's public IPv4 address
* arbitrary resources elsewhere in the VPC cannot connect to application port `8080`
* the task public IPv4 address remains an outbound-connectivity mechanism

### Application logs

The application container uses the ECS `awslogs` log driver.

Logs are sent to a Terraform-managed CloudWatch Logs group with:

* a descriptive application log-group name
* a seven-day retention period
* the configured AWS region
* an `application` log-stream prefix

CloudWatch alarms, dashboards, Container Insights, and AWS-hosted Prometheus or Grafana are not configured.

## Application Load Balancer and public access

The root module defines one internet-facing Application Load Balancer across the two public subnets.

The load balancer uses:

* IPv4
* an HTTP listener on TCP port `80`
* one HTTP target group on port `8080`
* target type `ip`
* the ECS Fargate task network interfaces as targets

### Public request path

Application traffic follows this path:

```text
Internet
  -> Application Load Balancer: TCP 80
  -> ECS application target: TCP 8080
  -> RDS PostgreSQL: TCP 5432
```

The load-balancer listener forwards every HTTP request to the application target group.

### Security groups

The load-balancer security group:

* accepts inbound TCP port `80` from `0.0.0.0/0`
* allows outbound TCP port `8080` only to the application security group

The application security group:

* accepts inbound TCP port `8080` only from the load-balancer security group
* does not accept application traffic directly from the public internet, VPC CIDR, or subnet CIDRs

The database security group continues to accept TCP port `5432` only from the application security group.

### Target-group health checks

The target group checks:

```text
/actuator/health/readiness
```

The health-check configuration uses:

* HTTP
* expected status code `200`
* a 30-second interval
* a 5-second timeout
* two consecutive successful checks to become healthy
* three consecutive unsuccessful checks to become unhealthy

The ECS service uses a 120-second health-check grace period. During that period, ECS ignores unsuccessful
load-balancer health checks while Spring Boot, Flyway, and the datasource initialize.

The load balancer forwards user traffic only to targets that pass the readiness health check.

### Obtain the public application URL

After the infrastructure exists, retrieve the complete HTTP URL:

```bash
terraform -chdir=terraform output -raw application_url
```

The load-balancer DNS name is also available separately:

```bash
terraform -chdir=terraform output -raw load_balancer_dns_name
```

The current public endpoint uses HTTP only. No custom domain, TLS certificate, HTTPS listener, or HTTP-to-HTTPS redirect
is configured.

## Outputs

| Output | Purpose |
|---|---|
| `vpc_id` | Project VPC identifier |
| `public_subnet_ids`, `private_subnet_ids` | Subnet identifiers in position order |
| `ecr_repository_url` | Image publishing and deployment target |
| `database_endpoint`, `database_port`, `database_name` | Application database connection coordinates |
| `database_master_secret_arn` | Identifier of the RDS-managed secret; it does not expose the value |
| `ecs_cluster_name`, `ecs_service_name` | ECS runtime identifiers |
| `application_log_group_name` | CloudWatch application log group |
| `load_balancer_dns_name`, `application_url` | Public load-balancer hostname and HTTP URL |

## Remote state

The root module contains a partial Amazon S3 backend declaration. The repository does not contain a real bucket name or
activate the remote backend automatically.

Before using the S3 backend, its bucket must already exist. The bucket should have:

* versioning enabled so previous state versions can be recovered
* public access blocked
* server-side encryption enabled
* access restricted to the users and automation that manage this infrastructure

Each environment should use a distinct state key. For example:

```text
java-cloud-platform-lab/dev/terraform.tfstate
java-cloud-platform-lab/staging/terraform.tfstate
java-cloud-platform-lab/prod/terraform.tfstate
```

Backend configuration cannot reference Terraform input variables or locals. It is supplied separately during
initialization.

Copy the example backend configuration:

```bash
cp terraform/backend.s3.tfbackend.example terraform/backend.s3.tfbackend
```

Edit `terraform/backend.s3.tfbackend` and replace the placeholder bucket name and any environment-specific settings.

The local `.tfbackend` file is ignored by Git. It must not contain credentials or secrets.

After the state bucket is available, initialize the backend from the repository root:

```bash
terraform -chdir=terraform init \
  -reconfigure \
  -backend-config=backend.s3.tfbackend
```

Do not run this command with the placeholder example values. Creating the bucket and migrating any existing state are
separate tasks.

## State locking

The example backend configuration enables S3-native state locking:

```hcl
use_lockfile = true
```

Terraform uses a lock file in the S3 bucket to prevent concurrent operations from writing the same state.

DynamoDB-based state locking is not used.

The S3 state lock file is unrelated to `.terraform.lock.hcl`:

* the S3 lock file protects remote state from concurrent modification
* `.terraform.lock.hcl` records selected provider versions and package checksums

## Format the configuration

From the repository root:

```bash
terraform -chdir=terraform fmt -recursive
```

Verify formatting:

```bash
terraform -chdir=terraform fmt -check -recursive
```

## Initialize without the remote backend

Local validation and CI can initialize Terraform without configuring or contacting the S3 backend:

```bash
terraform -chdir=terraform init \
  -backend=false \
  -input=false \
  -lockfile=readonly
```

Initialization downloads the required provider and uses the committed `.terraform.lock.hcl` without modifying it.

The dependency lock file is intentionally committed so provider-version selections and checksum changes can be reviewed.

The generated `.terraform/` working directory must not be committed.

## Validate the configuration

```bash
terraform -chdir=terraform validate -no-color
```

Validation checks that the configuration is syntactically valid and internally consistent. It does not provision or
modify infrastructure.

The network, ECR, RDS, IAM, ECS, load-balancer, target-group, listener, security-group, and CloudWatch Logs resources
are created only when `terraform apply` is run with valid AWS credentials.

## Current limitations

The Terraform design remains intentionally development-oriented:

* ECS uses one public-subnet task with a public IPv4 address; no NAT, VPC endpoints, autoscaling, ECS Exec, or separate
  application task role is configured.
* Public access is HTTP-only, without a custom domain, TLS, WAF, or load-balancer authentication.
* RDS is single-AZ and disposable, without retained automated backups or a final snapshot.
* CloudWatch alarms, dashboards, Container Insights, and AWS-hosted Prometheus or Grafana are not configured.
* Image publishing, deployment, and post-rotation ECS replacement are manual.
* The remote-state bucket, state migration, modules, and environment-specific directory structure are managed outside
  this root module.

Project-wide context and operational procedures are documented in [Architecture](../docs/architecture.md) and [Operations](../docs/operations.md).
