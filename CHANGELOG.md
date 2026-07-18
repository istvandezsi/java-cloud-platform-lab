# Changelog

This document records stable releases of Java Cloud Platform Lab.

## v1.0.0 - 2026-07-18

The first stable release of the project.

### Application

- Java 21 and Spring Boot task-board application;
- browser interface and REST API with validation and consistent error responses;
- OpenAPI documentation and Swagger UI;
- Spring JDBC persistence with PostgreSQL;
- Flyway-managed database migrations;
- readiness, liveness, general health, and Prometheus-format metrics.

### Runtime and platform

- production-style Docker image;
- version-independent Docker image assembly that copies the Maven-built application JAR without embedding the release version in the Dockerfile;
- Docker Compose environment with the application, PostgreSQL, Prometheus, and Grafana;
- Kubernetes Deployment, Service, ConfigMap, Secret, health probes, and resource constraints;
- Terraform-managed AWS development architecture with:
  - an Application Load Balancer;
  - an ECS Fargate service;
  - a private RDS PostgreSQL database;
  - ECR;
  - RDS-managed credentials in Secrets Manager;
  - CloudWatch application logs;
  - supporting networking, IAM, and security groups;
- reusable AWS deploy–verify–destroy runbook.

### Verification and CI

- Maven application and API tests;
- H2-based tests and Testcontainers PostgreSQL integration;
- Docker image and Docker Compose validation;
- Kubernetes schema validation;
- Prometheus configuration and alert-rule validation;
- Terraform formatting, initialization, and configuration validation;
- offline Markdown link and anchor validation;
- completed AWS live verification covering:
  - application availability through the load balancer;
  - Flyway startup and PostgreSQL persistence;
  - persistence across ECS task replacement;
  - CloudWatch logging;
  - security-group boundaries;
  - zero Terraform drift;
  - complete infrastructure teardown.

### Intended use and boundaries

The repository is a practical platform-engineering reference implementation for
learning, experimentation, and adaptation. It is not a production-ready
platform, and the AWS environment is not kept running.

See [Architecture](docs/architecture.md) for the design boundaries and
[AWS Live Verification](docs/aws-live-verification.md) for the completed
verification procedure and record.
