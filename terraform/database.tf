resource "aws_db_subnet_group" "database" {
  name        = "${local.name_prefix}-database"
  description = "Private subnet group for the project PostgreSQL database"

  subnet_ids = [
    for position in ["1", "2"] :
    aws_subnet.private[position].id
  ]

  tags = {
    Name = "${local.name_prefix}-database-subnet-group"
  }
}

resource "aws_security_group" "database" {
  name        = "${local.name_prefix}-database"
  description = "Security group for the project PostgreSQL database"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-database-sg"
  }
}

resource "aws_db_instance" "database" {
  identifier = "${local.name_prefix}-database"

  engine         = "postgres"
  instance_class = var.database_instance_class

  db_name  = var.database_name
  username = var.database_master_username
  port     = 5432

  manage_master_user_password = true

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  multi_az            = false
  publicly_accessible = false

  db_subnet_group_name   = aws_db_subnet_group.database.name
  vpc_security_group_ids = [aws_security_group.database.id]

  deletion_protection = false
  skip_final_snapshot = true

  tags = {
    Name = "${local.name_prefix}-database"
  }
}
