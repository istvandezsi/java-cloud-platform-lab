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
