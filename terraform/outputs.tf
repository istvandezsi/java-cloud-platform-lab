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

output "ecs_cluster_name" {
  description = "Name of the ECS cluster running the application."
  value       = aws_ecs_cluster.application.name
}

output "ecs_service_name" {
  description = "Name of the ECS Fargate application service."
  value       = aws_ecs_service.application.name
}

output "application_log_group_name" {
  description = "Name of the CloudWatch Logs group containing application container logs."
  value       = aws_cloudwatch_log_group.application.name
}

output "load_balancer_dns_name" {
  description = "Public DNS name of the Application Load Balancer."
  value       = aws_lb.application.dns_name
}

output "application_url" {
  description = "Public HTTP URL of the application."
  value       = "http://${aws_lb.application.dns_name}"
}
