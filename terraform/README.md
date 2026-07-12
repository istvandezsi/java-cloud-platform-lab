# Terraform

This directory contains the Terraform root module for the Java Cloud Platform Lab AWS infrastructure.

The current configuration establishes only the Terraform and AWS provider requirements, shared input variables, resource
naming conventions, and common tags. It intentionally provisions no AWS resources.

## Prerequisites

Install:

* Terraform 1.15 or a later compatible 1.x release
* AWS CLI, when AWS authentication or resource operations are introduced

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

Do not add AWS access keys, secret keys, or session tokens to Terraform configuration or variable files.

The AWS provider can obtain credentials through standard AWS credential sources, including:

* environment variables
* the shared AWS credentials and configuration files
* container credentials
* an attached IAM role

For a locally configured AWS CLI profile, the provider can use the profile selected through the `AWS_PROFILE`
environment variable.

The current configuration does not create infrastructure. AWS authentication will become relevant when later changes
introduce AWS resources and Terraform plan or apply operations.

## Format the configuration

From the repository root:

```bash
terraform -chdir=terraform fmt -recursive
```

Verify formatting:

```bash
terraform -chdir=terraform fmt -check -recursive
```

## Initialize Terraform

Initialize the root module without configuring a remote backend:

```bash
terraform -chdir=terraform init -backend=false
```

Initialization downloads the required provider and creates or updates `.terraform.lock.hcl`.

The dependency lock file is intentionally committed so provider-version selections and checksum changes can be reviewed.

The generated `.terraform/` working directory must not be committed.

## Validate the configuration

```bash
terraform -chdir=terraform validate
```

Validation checks that the configuration is syntactically valid and internally consistent. It does not provision or
modify infrastructure.

## Current limitations

This initial Terraform structure intentionally contains:

* no AWS resources
* no remote-state backend
* no modules
* no environment-specific directories
* no outputs
* no deployment workflow

Remote state and actual AWS infrastructure will be introduced through separate follow-up changes.
