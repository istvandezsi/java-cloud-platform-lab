resource "aws_security_group" "load_balancer" {
  name        = "${local.name_prefix}-load-balancer"
  description = "Security group for the public Application Load Balancer"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-load-balancer-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "load_balancer_http" {
  security_group_id = aws_security_group.load_balancer.id

  description = "Allow public HTTP traffic"
  cidr_ipv4   = "0.0.0.0/0"
  from_port   = 80
  ip_protocol = "tcp"
  to_port     = 80
}

resource "aws_vpc_security_group_egress_rule" "load_balancer_to_application" {
  security_group_id = aws_security_group.load_balancer.id

  description                  = "Allow HTTP traffic to the ECS application"
  referenced_security_group_id = aws_security_group.application.id
  from_port                    = 8080
  ip_protocol                  = "tcp"
  to_port                      = 8080
}

resource "aws_vpc_security_group_ingress_rule" "application_from_load_balancer" {
  security_group_id = aws_security_group.application.id

  description                  = "Allow HTTP traffic from the Application Load Balancer"
  referenced_security_group_id = aws_security_group.load_balancer.id
  from_port                    = 8080
  ip_protocol                  = "tcp"
  to_port                      = 8080
}

resource "aws_lb" "application" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  ip_address_type    = "ipv4"

  security_groups = [aws_security_group.load_balancer.id]

  subnets = [
    for position in ["1", "2"] :
    aws_subnet.public[position].id
  ]

  depends_on = [
    aws_route.public_internet,
    aws_route_table_association.public
  ]

  tags = {
    Name = "${local.name_prefix}-alb"
  }
}

resource "aws_lb_target_group" "application" {
  name        = "${local.name_prefix}-app"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health/readiness"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${local.name_prefix}-application-targets"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.application.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.application.arn
  }
}
