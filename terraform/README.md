# Terraform

This directory contains the Terraform root module for the Java Cloud Platform Lab AWS infrastructure.

The current configuration establishes the Terraform and AWS provider requirements, shared input variables, resource
naming conventions, common tags, a partial Amazon S3 backend declaration, and the foundational VPC network.

## Prerequisites

Install:

* Terraform 1.15 or a later compatible 1.x release
* AWS CLI for AWS authentication and resource operations

Confirm the Terraform installation:

```bash
terraform version
```

## Local configuration

Copy the example variable file:

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars
```

Edit `terraform/terraform.tfvars` when different local values are required.

The local `terraform.tfvars` file is ignored by Git and must not contain committed credentials or secrets.

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
when workloads require outbound connectivity.

The VPC, subnets, internet gateway, and route tables inherit the provider-level common tags and receive descriptive
`Name` tags.

## Outputs

The root module exposes:

* `vpc_id`
* `public_subnet_ids`
* `private_subnet_ids`

Subnet IDs are returned in position order.

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

The network resources are created only when `terraform apply` is run with valid AWS credentials.

## Current limitations

The current Terraform configuration intentionally contains:

* no NAT gateway or NAT instance
* no VPC endpoints
* no security groups or custom network ACLs
* no IPv6 configuration
* no load balancer, compute, or database resources
* no state-bucket provisioning
* no active remote-state configuration
* no state migration
* no modules
* no environment-specific directories
* no deployment workflow

Additional infrastructure and remote-state activation will be introduced through separate follow-up changes.
