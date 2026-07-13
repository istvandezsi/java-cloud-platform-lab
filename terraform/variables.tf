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
