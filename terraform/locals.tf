locals {
  name_prefix        = "${var.project_name}-${var.environment}"
  availability_zones = slice(sort(data.aws_availability_zones.available.names), 0, 2)

  public_subnets = {
    "1" = {
      availability_zone = local.availability_zones[0]
      cidr_block        = cidrsubnet(var.vpc_cidr, 8, 0)
    }
    "2" = {
      availability_zone = local.availability_zones[1]
      cidr_block        = cidrsubnet(var.vpc_cidr, 8, 1)
    }
  }

  private_subnets = {
    "1" = {
      availability_zone = local.availability_zones[0]
      cidr_block        = cidrsubnet(var.vpc_cidr, 8, 10)
    }
    "2" = {
      availability_zone = local.availability_zones[1]
      cidr_block        = cidrsubnet(var.vpc_cidr, 8, 11)
    }
  }

  common_tags = {
    Environment = var.environment
    ManagedBy   = "Terraform"
    Project     = var.project_name
  }
}
