variable "aws_region" {
  description = "AWS region in which resources will be managed."
  type        = string
  nullable    = false

  validation {
    condition = (
      var.aws_region == trimspace(var.aws_region) &&
      can(regex("^[a-z]{2}(-[a-z0-9]+)+-[0-9]+$", var.aws_region))
    )
    error_message = "aws_region must be a valid AWS region identifier without surrounding whitespace."
  }
}

variable "environment" {
  description = "Deployment environment used for resource naming and tagging."
  type        = string
  nullable    = false

  validation {
    condition = (
      var.environment == trimspace(var.environment) &&
      can(regex("^[a-z]([a-z0-9-]*[a-z0-9])?$", var.environment)) &&
      !strcontains(var.environment, "--")
    )
    error_message = "environment must start with a lowercase letter, contain only lowercase letters, numbers, or single hyphens, and end with a letter or number."
  }
}

variable "project_name" {
  description = "Project name used for resource naming and tagging."
  type        = string
  nullable    = false

  validation {
    condition = (
      var.project_name == trimspace(var.project_name) &&
      can(regex("^[a-z]([a-z0-9-]*[a-z0-9])?$", var.project_name)) &&
      !strcontains(var.project_name, "--")
    )
    error_message = "project_name must start with a lowercase letter, contain only lowercase letters, numbers, or single hyphens, and end with a letter or number."
  }

  validation {
    condition     = length("${var.project_name}-${var.environment}") <= 27
    error_message = "project_name and environment must form a resource name prefix no longer than 27 characters."
  }
}

variable "application_image_tag" {
  description = "Immutable tag of an application image already published to the ECR repository."
  type        = string
  nullable    = false

  validation {
    condition = (
      var.application_image_tag == trimspace(var.application_image_tag) &&
      can(regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$", var.application_image_tag))
    )
    error_message = "application_image_tag must be a valid container image tag of at most 128 characters."
  }
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block used for the VPC and derived subnets."
  type        = string
  nullable    = false

  validation {
    condition = (
      can(cidrnetmask(var.vpc_cidr)) &&
      try(tonumber(split("/", var.vpc_cidr)[1]), 99) >= 16 &&
      try(tonumber(split("/", var.vpc_cidr)[1]), 99) <= 20 &&
      try(cidrhost(var.vpc_cidr, 0), "") == try(split("/", var.vpc_cidr)[0], "")
    )
    error_message = "vpc_cidr must be a canonical IPv4 CIDR block between /16 and /20 so the derived subnets remain valid AWS /24 to /28 networks."
  }
}

variable "database_name" {
  description = "Name of the PostgreSQL application database."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9]{0,62}$", var.database_name))
    error_message = "database_name must start with a letter and contain 1 to 63 alphanumeric characters."
  }
}

variable "database_master_username" {
  description = "Master username for the PostgreSQL database."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[A-Za-z][A-Za-z0-9]{0,62}$", var.database_master_username))
    error_message = "database_master_username must start with a letter and contain 1 to 63 alphanumeric characters."
  }
}

variable "database_instance_class" {
  description = "Instance class used by the PostgreSQL database."
  type        = string
  nullable    = false

  validation {
    condition = (
      var.database_instance_class == trimspace(var.database_instance_class) &&
      can(regex("^db\\.[a-z0-9]+\\.[a-z0-9]+$", var.database_instance_class))
    )
    error_message = "database_instance_class must use an RDS instance class identifier such as db.t4g.micro."
  }
}
