output "vpc_id" {
  description = "ID of the project VPC."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets in position order."
  value = [
    for position in ["1", "2"] :
    aws_subnet.public[position].id
  ]
}

output "private_subnet_ids" {
  description = "IDs of the private subnets in position order."
  value = [
    for position in ["1", "2"] :
    aws_subnet.private[position].id
  ]
}

output "ecr_repository_url" {
  description = "URL of the ECR repository for the application image."
  value       = aws_ecr_repository.application.repository_url
}

output "database_endpoint" {
  description = "DNS address of the PostgreSQL database."
  value       = aws_db_instance.database.address
}

output "database_port" {
  description = "Port on which the PostgreSQL database accepts connections."
  value       = aws_db_instance.database.port
}

output "database_name" {
  description = "Name of the PostgreSQL application database."
  value       = aws_db_instance.database.db_name
}

output "database_master_secret_arn" {
  description = "ARN of the Secrets Manager secret containing the database master credentials."
  value       = aws_db_instance.database.master_user_secret[0].secret_arn
}
