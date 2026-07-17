variable "aws_region" {
  description = "AWS region in which resources will be managed."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.aws_region)) > 0
    error_message = "aws_region must not be blank."
  }
}

variable "environment" {
  description = "Deployment environment used for resource naming and tagging."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.environment)) > 0
    error_message = "environment must not be blank."
  }
}

variable "project_name" {
  description = "Project name used for resource naming and tagging."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.project_name)) > 0
    error_message = "project_name must not be blank."
  }
}

variable "application_image_tag" {
  description = "Immutable tag of an application image already published to the ECR repository."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.application_image_tag)) > 0
    error_message = "application_image_tag must not be blank."
  }
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block used for the VPC and derived subnets."
  type        = string
  nullable    = false

  validation {
    condition = (
      can(cidrnetmask(var.vpc_cidr)) &&
      can(cidrsubnet(var.vpc_cidr, 8, 11))
    )
    error_message = "vpc_cidr must be a valid IPv4 CIDR block with enough address space for the derived subnets."
  }
}

variable "database_name" {
  description = "Name of the PostgreSQL application database."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.database_name)) > 0
    error_message = "database_name must not be blank."
  }
}

variable "database_master_username" {
  description = "Master username for the PostgreSQL database."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.database_master_username)) > 0
    error_message = "database_master_username must not be blank."
  }
}

variable "database_instance_class" {
  description = "Instance class used by the PostgreSQL database."
  type        = string
  nullable    = false

  validation {
    condition     = length(trimspace(var.database_instance_class)) > 0
    error_message = "database_instance_class must not be blank."
  }
}
