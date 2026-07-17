resource "aws_ecs_cluster" "application" {
  name = local.name_prefix

  tags = {
    Name = "${local.name_prefix}-ecs-cluster"
  }
}

resource "aws_cloudwatch_log_group" "application" {
  name              = "/ecs/${local.name_prefix}/application"
  retention_in_days = 7

  tags = {
    Name = "${local.name_prefix}-application-logs"
  }
}

resource "aws_security_group" "application" {
  name        = "${local.name_prefix}-application"
  description = "Security group for the ECS application tasks"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-application-sg"
  }
}

resource "aws_vpc_security_group_egress_rule" "application" {
  security_group_id = aws_security_group.application.id

  description = "Allow application outbound traffic"
  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "database_from_application" {
  security_group_id = aws_security_group.database.id

  description                  = "Allow PostgreSQL access from the ECS application"
  referenced_security_group_id = aws_security_group.application.id
  from_port                    = 5432
  ip_protocol                  = "tcp"
  to_port                      = 5432
}

resource "aws_ecs_task_definition" "application" {
  family                   = "${local.name_prefix}-application"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"

  cpu    = "256"
  memory = "512"

  execution_role_arn = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "application"
      image     = "${aws_ecr_repository.application.repository_url}:${var.application_image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.database.address}:${aws_db_instance.database.port}/${aws_db_instance.database.db_name}"
        }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${aws_db_instance.database.master_user_secret[0].secret_arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_db_instance.database.master_user_secret[0].secret_arn}:password::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.application.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "application"
        }
      }
    }
  ])

  tags = {
    Name = "${local.name_prefix}-application-task"
  }
}

resource "aws_ecs_service" "application" {
  name            = "${local.name_prefix}-application"
  cluster         = aws_ecs_cluster.application.id
  task_definition = aws_ecs_task_definition.application.arn

  desired_count                     = 1
  launch_type                       = "FARGATE"
  platform_version                  = "1.4.0"
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets = [
      for position in ["1", "2"] :
      aws_subnet.public[position].id
    ]

    security_groups  = [aws_security_group.application.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.application.arn
    container_name   = "application"
    container_port   = 8080
  }

  depends_on = [
    aws_iam_role_policy_attachment.ecs_task_execution,
    aws_iam_role_policy.database_secret_access,
    aws_vpc_security_group_egress_rule.application,
    aws_vpc_security_group_ingress_rule.database_from_application,
    aws_vpc_security_group_egress_rule.load_balancer_to_application,
    aws_vpc_security_group_ingress_rule.application_from_load_balancer,
    aws_lb_listener.http,
    aws_route.public_internet,
    aws_route_table_association.public
  ]

  tags = {
    Name = "${local.name_prefix}-application-service"
  }
}
