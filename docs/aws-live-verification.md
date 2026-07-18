# AWS Live Verification

This runbook verifies the Terraform-managed AWS environment through a
controlled deploy, runtime test, persistence test, and same-session teardown.

It covers:

- Terraform and ECR bootstrap;
- ECS Fargate deployment behind an Application Load Balancer;
- RDS PostgreSQL and Flyway verification;
- persistence across ECS task replacement;
- CloudWatch logging;
- security-group boundaries;
- Terraform drift detection;
- Terraform destruction and AWS-side cleanup.

For architecture and configuration details, see
[Terraform](../terraform/README.md). For general operational commands, see
[Operations](operations.md).

## Safety

This procedure creates billable AWS resources, including an Application Load
Balancer, an ECS Fargate task, and an RDS PostgreSQL instance.

Complete the deployment, verification, and destruction in one session. Do not
leave the environment running after the exercise.

The database is disposable. Terraform deletes it without a final snapshot.

Saved Terraform plans may contain sensitive configuration. Keep them
uncommitted and remove them after the exercise.

Do not place any of the following in this document or in committed
configuration:

- AWS account numbers;
- access keys, session tokens, or passwords;
- generated secret names or secret values;
- concrete ARNs or resource IDs;
- temporary ALB or RDS hostnames.

## Shell compatibility

Commands in this runbook assume Bash.

The commands were originally verified using Git Bash on Windows. Git
Bash-specific caveats are called out where needed.

PowerShell commands are not directly copy-paste compatible. In PowerShell:

- use `$env:NAME = "value"` instead of `export NAME="value"`;
- use a backtick instead of `\` for line continuation;
- read native command exit codes from `$LASTEXITCODE`;
- use `curl.exe` when `curl` resolves to a PowerShell alias;
- do not use `MSYS2_ARG_CONV_EXCL`, which is specific to Git Bash.

Other shells may require equivalent quoting, variable, and pipeline syntax.

## Prerequisites

Install:

- Git;
- Docker;
- Terraform 1.x;
- AWS CLI;
- Bash or a compatible shell;
- `curl`.

Verify the tools:

```bash
git --version
docker version
terraform version
aws --version
curl --version
```

The following local files must exist:

```text
terraform/backend.s3.tfbackend
terraform/terraform.tfvars
```

Create `terraform/terraform.tfvars` from the committed example when necessary:

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars
```

Both local configuration files must remain uncommitted.

The variables file must not contain AWS credentials or database passwords.
RDS generates and manages the database master password through Secrets
Manager.

## 1. Prepare the repository and AWS session

Start from the repository root.

Verify the checked-out revision and working tree:

```bash
git status --short
git log -1 --oneline
```

The working tree should be clean.

Select the AWS CLI profile:

```bash
export AWS_PROFILE="<aws-profile>"
```

Authenticate using the method configured for that profile. For an AWS CLI
browser-login profile:

```bash
aws login --profile "$AWS_PROFILE"
```

Verify the selected identity:

```bash
aws sts get-caller-identity \
  --query '{Account:Account,Arn:Arn}' \
  --output json
```

Stop when:

- the account is unexpected;
- the identity is the root user;
- authentication has expired.

## 2. Initialize Terraform

Initialize the partial S3 backend:

```bash
terraform -chdir=terraform init \
  -reconfigure \
  -backend-config=backend.s3.tfbackend \
  -input=false \
  -lockfile=readonly
```

Validate the configuration:

```bash
terraform -chdir=terraform validate
```

Verify the workspace:

```bash
terraform -chdir=terraform workspace show
```

The expected workspace is:

```text
default
```

Read reusable values from the loaded Terraform variables:

```bash
terraform_value() {
  printf 'var.%s\n' "$1" \
    | terraform -chdir=terraform console -no-color \
    | tr -d '"\r'
}

export AWS_REGION="$(terraform_value aws_region)"
export PROJECT_NAME="$(terraform_value project_name)"
export ENVIRONMENT="$(terraform_value environment)"
export PROJECT_PREFIX="${PROJECT_NAME}-${ENVIRONMENT}"
```

Verify the derived configuration:

```bash
printf 'AWS_REGION=%s\n' "$AWS_REGION"
printf 'PROJECT_NAME=%s\n' "$PROJECT_NAME"
printf 'ENVIRONMENT=%s\n' "$ENVIRONMENT"
```

Inspect the existing Terraform state:

```bash
if ! STATE_ENTRIES="$(
  terraform -chdir=terraform state list
)"; then
  echo "Unable to read Terraform state."
  exit 1
fi

printf '%s\n' "$STATE_ENTRIES"

if [ -n "$STATE_ENTRIES" ]; then
  echo "Terraform state is not empty."
  exit 1
fi

echo "Terraform state is empty."
```

A new verification environment should have no existing state entries. Stop and
investigate unexpected state rather than deploying over it.

Check for tagged project resources:

```bash
aws resourcegroupstaggingapi get-resources \
  --region "$AWS_REGION" \
  --tag-filters \
    "Key=Project,Values=$PROJECT_NAME" \
    "Key=Environment,Values=$ENVIRONMENT" \
  --query 'ResourceTagMappingList[].ResourceARN' \
  --output table
```

The tagging API may return historical records for deleted resources. Verify
unexpected results with the relevant service-specific AWS command.

## 3. Bootstrap the AWS environment

The ECR repository does not exist before the first Terraform apply. Set the
image tag in the ignored `terraform/terraform.tfvars` file to a temporary,
nonexistent tag:

```hcl
application_image_tag = "bootstrap-placeholder"
```

Verify the value:

```bash
grep '^application_image_tag' terraform/terraform.tfvars
git status --short
```

Create a saved bootstrap plan:

```bash
rm -f terraform/bootstrap.tfplan

terraform -chdir=terraform plan \
  -input=false \
  -lock-timeout=30s \
  -out=bootstrap.tfplan
```

Review the summary:

```bash
terraform -chdir=terraform show \
  -no-color \
  bootstrap.tfplan \
  | grep -E '^  # |^Plan:|^Warning:|^Error:'
```

The plan must contain only expected additions. It must not contain changes or
destructions. Review the complete plan when any action is unclear:

```bash
terraform -chdir=terraform show -no-color bootstrap.tfplan | less
```

**AWS credentials required**

**May incur AWS costs**

Apply the exact saved plan:

```bash
terraform -chdir=terraform apply \
  -input=false \
  bootstrap.tfplan
```

RDS and the load balancer may take several minutes to create.

Read the ECS identifiers:

```bash
ECS_CLUSTER_NAME="$(
  terraform -chdir=terraform output -raw ecs_cluster_name
)"

ECS_SERVICE_NAME="$(
  terraform -chdir=terraform output -raw ecs_service_name
)"
```

Inspect the bootstrap service:

```bash
aws ecs describe-services \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER_NAME" \
  --services "$ECS_SERVICE_NAME" \
  --query 'services[0].{
    status:status,
    desired:desiredCount,
    running:runningCount,
    pending:pendingCount,
    latestEvents:events[0:5].message
  }' \
  --output json
```

An image-pull failure mentioning `bootstrap-placeholder` is expected at this
stage. The real image has not yet been published.

## 4. Build and publish the application image

Use the complete checked-out Git commit SHA as the immutable image tag:

```bash
export IMAGE_TAG="$(git rev-parse HEAD)"
```

Read the ECR repository URL:

```bash
export ECR_REPOSITORY_URL="$(
  terraform -chdir=terraform output -raw ecr_repository_url
)"

export ECR_REGISTRY="${ECR_REPOSITORY_URL%%/*}"
export ECR_REPOSITORY_NAME="${ECR_REPOSITORY_URL#*/}"
```

Verify the derived values without recording them in committed files:

```bash
printf 'IMAGE_TAG=%s\n' "$IMAGE_TAG"
printf 'ECR_REPOSITORY_NAME=%s\n' "$ECR_REPOSITORY_NAME"
```

Authenticate Docker to ECR:

```bash
aws ecr get-login-password \
  --region "$AWS_REGION" \
  | docker login \
      --username AWS \
      --password-stdin "$ECR_REGISTRY"
```

Build the application image:

```bash
docker build \
  --platform linux/amd64 \
  --tag "$PROJECT_NAME:$IMAGE_TAG" \
  .
```

The current ECS task definition uses the default Fargate x86-64 runtime. Update
the build platform if a future Terraform `runtime_platform` setting changes
the architecture.

Verify the local image:

```bash
docker image inspect \
  "$PROJECT_NAME:$IMAGE_TAG" \
  --format 'OS={{.Os}} ARCH={{.Architecture}} SIZE={{.Size}}'
```

Expected architecture:

```text
amd64
```

Tag and push the image:

```bash
docker tag \
  "$PROJECT_NAME:$IMAGE_TAG" \
  "$ECR_REPOSITORY_URL:$IMAGE_TAG"

docker push "$ECR_REPOSITORY_URL:$IMAGE_TAG"
```

Verify that ECR contains the image and reports a digest:

```bash
aws ecr describe-images \
  --region "$AWS_REGION" \
  --repository-name "$ECR_REPOSITORY_NAME" \
  --image-ids "imageTag=$IMAGE_TAG" \
  --query 'imageDetails[0].{
    tags:imageTags,
    digest:imageDigest,
    pushedAt:imagePushedAt,
    sizeBytes:imageSizeInBytes
  }' \
  --output json
```

## 5. Deploy the published image

Set `application_image_tag` in the ignored
`terraform/terraform.tfvars` file to the value printed by:

```bash
printf '%s\n' "$IMAGE_TAG"
```

The resulting assignment should have this form:

```hcl
application_image_tag = "<current-git-commit-sha>"
```

Verify it:

```bash
grep '^application_image_tag' terraform/terraform.tfvars
git status --short
```

Create a saved deployment plan:

```bash
rm -f terraform/deploy.tfplan

terraform -chdir=terraform plan \
  -input=false \
  -lock-timeout=30s \
  -out=deploy.tfplan
```

Review the summary:

```bash
terraform -chdir=terraform show \
  -no-color \
  deploy.tfplan \
  | grep -E '^  # |^Plan:|^Warning:|^Error:'
```

When only the image tag changed, the expected actions are:

- replacement of the ECS task definition;
- an in-place ECS service update.

The plan must not replace the database, load balancer, VPC, subnets, security
groups, ECR repository, or IAM resources. Review the complete plan when any
action is unclear:

```bash
terraform -chdir=terraform show -no-color deploy.tfplan | less
```

**AWS credentials required**

**May incur AWS costs**

Apply the exact saved plan:

```bash
terraform -chdir=terraform apply \
  -input=false \
  deploy.tfplan
```

Wait for ECS service stability:

```bash
aws ecs wait services-stable \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER_NAME" \
  --services "$ECS_SERVICE_NAME"
```

Inspect the service:

```bash
aws ecs describe-services \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER_NAME" \
  --services "$ECS_SERVICE_NAME" \
  --query 'services[0].{
    status:status,
    desired:desiredCount,
    running:runningCount,
    pending:pendingCount,
    taskDefinition:taskDefinition,
    deployments:deployments[].{
      status:status,
      rolloutState:rolloutState,
      desired:desiredCount,
      running:runningCount,
      pending:pendingCount
    }
  }' \
  --output json
```

Expected state:

```text
status: ACTIVE
desired: 1
running: 1
pending: 0
rolloutState: COMPLETED
```

Historical bootstrap image-pull errors may remain visible in the ECS event
history.

## 6. Verify ALB and application health

Read the target group:

```bash
TARGET_GROUP_ARN="$(
  aws ecs describe-services \
    --region "$AWS_REGION" \
    --cluster "$ECS_CLUSTER_NAME" \
    --services "$ECS_SERVICE_NAME" \
    --query 'services[0].loadBalancers[0].targetGroupArn' \
    --output text
)"
```

The ECS `services-stable` waiter does not guarantee that the target has
completed its ALB health checks.

Wait explicitly for target health:

```bash
aws elbv2 wait target-in-service \
  --region "$AWS_REGION" \
  --target-group-arn "$TARGET_GROUP_ARN"
```

Inspect the target:

```bash
aws elbv2 describe-target-health \
  --region "$AWS_REGION" \
  --target-group-arn "$TARGET_GROUP_ARN" \
  --query 'TargetHealthDescriptions[].{
    target:Target.Id,
    port:Target.Port,
    state:TargetHealth.State,
    reason:TargetHealth.Reason
  }' \
  --output table
```

Expected state:

```text
healthy
```

Read the application URL and log-group name:

```bash
APPLICATION_URL="$(
  terraform -chdir=terraform output -raw application_url
)"

APPLICATION_LOG_GROUP="$(
  terraform -chdir=terraform output -raw application_log_group_name
)"
```

Verify the API and health endpoints:

```bash
curl -fsS "$APPLICATION_URL/api/hello"
echo

curl -fsS "$APPLICATION_URL/actuator/health"
echo

curl -fsS "$APPLICATION_URL/actuator/health/readiness"
echo

curl -fsS "$APPLICATION_URL/actuator/health/liveness"
echo

curl -fsS "$APPLICATION_URL/api/tasks"
echo
```

Verify the remaining public endpoints:

```bash
for path in \
  "/" \
  "/swagger-ui.html" \
  "/v3/api-docs" \
  "/actuator/prometheus"
do
  printf '%-24s ' "$path"

  curl -sS \
    --output /dev/null \
    --write-out 'HTTP %{http_code}\n' \
    "$APPLICATION_URL$path"
done
```

Expected results:

- `/` returns HTTP 200;
- `/swagger-ui.html` may redirect with HTTP 302;
- `/v3/api-docs` returns HTTP 200;
- `/actuator/prometheus` returns HTTP 200.

## 7. Verify logs and database persistence

### CloudWatch logs

Git Bash may rewrite a slash-prefixed log-group name as a Windows path. Disable
MSYS argument conversion when calling AWS Logs commands:

```bash
MSYS2_ARG_CONV_EXCL='*' \
aws logs tail "$APPLICATION_LOG_GROUP" \
  --region "$AWS_REGION" \
  --since 30m \
  --format short \
  | grep -Ei \
      'started|flyway|hikari|postgres|migration|error|exception|warn'
```

Verify evidence of:

- Spring Boot startup;
- Hikari connection-pool startup;
- PostgreSQL connectivity;
- Flyway migration or validation;
- successful application startup;
- no repeating application errors.

For the first application task, Flyway should apply migration V1.

### Create a persistent record

Create a unique task:

```bash
export TASK_TITLE="AWS persistence check $(date +%s)"

curl -fsS \
  --request POST \
  "$APPLICATION_URL/api/tasks" \
  --header "Content-Type: application/json" \
  --data "{\"title\":\"$TASK_TITLE\"}"

echo
```

Verify that it exists:

```bash
curl -fsS "$APPLICATION_URL/api/tasks" \
  | grep --fixed-strings "$TASK_TITLE"

echo
```

### Replace the ECS task

Record the running task:

```bash
OLD_TASK_ARN="$(
  aws ecs list-tasks \
    --region "$AWS_REGION" \
    --cluster "$ECS_CLUSTER_NAME" \
    --service-name "$ECS_SERVICE_NAME" \
    --desired-status RUNNING \
    --query 'taskArns[0]' \
    --output text
)"
```

Stop it:

```bash
aws ecs stop-task \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER_NAME" \
  --task "$OLD_TASK_ARN" \
  --reason "Controlled PostgreSQL persistence verification" \
  --query 'task.{
    taskArn:taskArn,
    lastStatus:lastStatus
  }' \
  --output json
```

Wait for ECS and ALB recovery:

```bash
aws ecs wait services-stable \
  --region "$AWS_REGION" \
  --cluster "$ECS_CLUSTER_NAME" \
  --services "$ECS_SERVICE_NAME"

aws elbv2 wait target-in-service \
  --region "$AWS_REGION" \
  --target-group-arn "$TARGET_GROUP_ARN"
```

Capture the replacement task:

```bash
NEW_TASK_ARN="$(
  aws ecs list-tasks \
    --region "$AWS_REGION" \
    --cluster "$ECS_CLUSTER_NAME" \
    --service-name "$ECS_SERVICE_NAME" \
    --desired-status RUNNING \
    --query 'taskArns[0]' \
    --output text
)"

test "$OLD_TASK_ARN" != "$NEW_TASK_ARN" \
  && echo "ECS task replacement confirmed."
```

A temporary HTTP 502 can occur while the old target is draining and the new
target is becoming healthy.

Use a bounded readiness check:

```bash
READY_CODE=""

for attempt in {1..24}; do
  READY_CODE="$(
    curl \
      --silent \
      --show-error \
      --output readiness-response.json \
      --write-out '%{http_code}' \
      "$APPLICATION_URL/actuator/health/readiness" \
      || true
  )"

  printf 'Attempt %02d: HTTP %s\n' "$attempt" "$READY_CODE"

  if [ "$READY_CODE" = "200" ]; then
    cat readiness-response.json
    echo
    break
  fi

  sleep 5
done

test "$READY_CODE" = "200"
```

Verify that the original database record survived:

```bash
curl -fsS "$APPLICATION_URL/api/tasks" \
  | grep --fixed-strings "$TASK_TITLE"

echo
```

Inspect the replacement startup logs:

```bash
MSYS2_ARG_CONV_EXCL='*' \
aws logs tail "$APPLICATION_LOG_GROUP" \
  --region "$AWS_REGION" \
  --since 15m \
  --format short \
  | grep -Ei \
      'started|flyway|hikari|postgres|migration|error|exception|warn'
```

The replacement task should report that the schema is already up to date and
that no new migration is required.

## 8. Verify security-group boundaries

Read the VPC and security-group IDs:

```bash
VPC_ID="$(
  terraform -chdir=terraform output -raw vpc_id
)"

LB_SG_ID="$(
  aws ec2 describe-security-groups \
    --region "$AWS_REGION" \
    --filters \
      "Name=vpc-id,Values=$VPC_ID" \
      "Name=group-name,Values=${PROJECT_PREFIX}-load-balancer" \
    --query 'SecurityGroups[0].GroupId' \
    --output text
)"

APP_SG_ID="$(
  aws ec2 describe-security-groups \
    --region "$AWS_REGION" \
    --filters \
      "Name=vpc-id,Values=$VPC_ID" \
      "Name=group-name,Values=${PROJECT_PREFIX}-application" \
    --query 'SecurityGroups[0].GroupId' \
    --output text
)"

DB_SG_ID="$(
  aws ec2 describe-security-groups \
    --region "$AWS_REGION" \
    --filters \
      "Name=vpc-id,Values=$VPC_ID" \
      "Name=group-name,Values=${PROJECT_PREFIX}-database" \
    --query 'SecurityGroups[0].GroupId' \
    --output text
)"
```

Verify each security group has exactly one expected ingress rule:

```bash
verify_single_ingress_rule() {
  local group_id="$1"
  local expected_filter="$2"
  local label="$3"
  local total_count
  local expected_count

  aws ec2 describe-security-group-rules \
    --region "$AWS_REGION" \
    --filters "Name=group-id,Values=$group_id" \
    --query "SecurityGroupRules[?IsEgress==\`false\`].{
      protocol:IpProtocol,
      from:FromPort,
      to:ToPort,
      cidr:CidrIpv4,
      sourceGroup:ReferencedGroupInfo.GroupId
    }" \
    --output table

  total_count="$(
    aws ec2 describe-security-group-rules \
      --region "$AWS_REGION" \
      --filters "Name=group-id,Values=$group_id" \
      --query "length(SecurityGroupRules[?IsEgress==\`false\`])" \
      --output text
  )"

  expected_count="$(
    aws ec2 describe-security-group-rules \
      --region "$AWS_REGION" \
      --filters "Name=group-id,Values=$group_id" \
      --query "length(SecurityGroupRules[?IsEgress==\`false\` && $expected_filter])" \
      --output text
  )"

  if [ "$total_count" != "1" ] || [ "$expected_count" != "1" ]; then
    echo "$label ingress verification failed."
    return 1
  fi

  echo "$label ingress verification passed."
}

verify_single_ingress_rule \
  "$LB_SG_ID" \
  "IpProtocol=='tcp' && FromPort==\`80\` && ToPort==\`80\` && CidrIpv4=='0.0.0.0/0'" \
  "ALB"

verify_single_ingress_rule \
  "$APP_SG_ID" \
  "IpProtocol=='tcp' && FromPort==\`8080\` && ToPort==\`8080\` && ReferencedGroupInfo.GroupId=='${LB_SG_ID}'" \
  "Application"

verify_single_ingress_rule \
  "$DB_SG_ID" \
  "IpProtocol=='tcp' && FromPort==\`5432\` && ToPort==\`5432\` && ReferencedGroupInfo.GroupId=='${APP_SG_ID}'" \
  "Database"
```

The expected ingress chain is:

```text
Internet -> ALB TCP 80
ALB security group -> ECS TCP 8080
ECS security group -> RDS TCP 5432
```

Keep JMESPath projections such as `[?...]` on one line. Splitting `[` and `?`
causes an AWS CLI query error.

## 9. Check for Terraform drift

Run a refresh-backed plan:

```bash
terraform -chdir=terraform plan \
  -input=false \
  -lock-timeout=30s \
  -detailed-exitcode

PLAN_EXIT=$?
printf 'Terraform detailed exit code: %s\n' "$PLAN_EXIT"
```

Expected result:

```text
No changes. Your infrastructure matches the configuration.
Terraform detailed exit code: 0
```

Exit codes:

- `0` — no changes;
- `1` — error;
- `2` — changes detected.

Investigate any nonzero result before destruction.

## 10. Create and apply the destruction plan

**AWS credentials required**

**Destructive**

Capture the real Secrets Manager name before Terraform outputs disappear:

```bash
DATABASE_SECRET_ARN="$(
  terraform -chdir=terraform output -raw database_master_secret_arn
)"

DATABASE_SECRET_NAME="$(
  aws secretsmanager describe-secret \
    --region "$AWS_REGION" \
    --secret-id "$DATABASE_SECRET_ARN" \
    --query 'Name' \
    --output text
)"
```

Do not print or commit the secret ARN or name. These values identify the
managed secret but do not retrieve its credential value.

Reconfirm the execution context:

```bash
aws sts get-caller-identity \
  --query '{Account:Account,Arn:Arn}' \
  --output json

printf 'AWS_REGION=%s\n' "$AWS_REGION"
terraform -chdir=terraform workspace show
```

Stop if the identity, region, or workspace is unexpected.

Create a saved destruction plan:

```bash
rm -f terraform/destroy.tfplan

terraform -chdir=terraform plan \
  -destroy \
  -input=false \
  -lock-timeout=30s \
  -out=destroy.tfplan
```

Review the summary:

```bash
terraform -chdir=terraform show \
  -no-color \
  destroy.tfplan \
  | grep -E '^  # |^Plan:|^Warning:|^Error:'
```

The summary must contain zero additions, zero changes, and the expected number
of destructions for the current configuration. Review the complete plan:

```bash
terraform -chdir=terraform show -no-color destroy.tfplan | less
```

Apply the exact reviewed plan:

```bash
terraform -chdir=terraform apply \
  -input=false \
  destroy.tfplan
```

ECS deletion may take several minutes while ALB targets drain. RDS deletion may
also take several minutes.

Verify that Terraform state is empty:

```bash
if ! STATE_ENTRIES="$(
  terraform -chdir=terraform state list
)"; then
  echo "Unable to read Terraform state after destruction."
  exit 1
fi

printf '%s\n' "$STATE_ENTRIES"

if [ -n "$STATE_ENTRIES" ]; then
  echo "Terraform state still contains managed objects."
  exit 1
fi

echo "Terraform state is empty."
```

A successful state read with no entries is required. An authentication,
backend, permission, or network error must not be treated as successful
cleanup.

## 11. Verify AWS-side cleanup

Terraform state being empty does not replace direct AWS verification.

Each of the following count commands should return `0`.

### RDS

```bash
aws rds describe-db-instances \
  --region "$AWS_REGION" \
  --query "length(DBInstances[?DBInstanceIdentifier=='${PROJECT_PREFIX}-database'])" \
  --output text
```

### Application Load Balancer

```bash
aws elbv2 describe-load-balancers \
  --region "$AWS_REGION" \
  --query "length(LoadBalancers[?LoadBalancerName=='${PROJECT_PREFIX}-alb'])" \
  --output text
```

### ECR repository

```bash
aws ecr describe-repositories \
  --region "$AWS_REGION" \
  --query "length(repositories[?repositoryName=='${PROJECT_PREFIX}'])" \
  --output text
```

### ECS cluster

```bash
aws ecs list-clusters \
  --region "$AWS_REGION" \
  --query "length(clusterArns[?contains(@, '${PROJECT_PREFIX}')])" \
  --output text
```

### Project VPC

```bash
aws ec2 describe-vpcs \
  --region "$AWS_REGION" \
  --filters \
    "Name=tag:Project,Values=$PROJECT_NAME" \
    "Name=tag:Environment,Values=$ENVIRONMENT" \
  --query 'length(Vpcs)' \
  --output text
```

### Network interfaces from the deleted VPC

```bash
aws ec2 describe-network-interfaces \
  --region "$AWS_REGION" \
  --filters "Name=vpc-id,Values=$VPC_ID" \
  --query 'length(NetworkInterfaces)' \
  --output text
```

### CloudWatch log group

```bash
MSYS2_ARG_CONV_EXCL='*' \
aws logs describe-log-groups \
  --region "$AWS_REGION" \
  --log-group-name-prefix "$APPLICATION_LOG_GROUP" \
  --query 'length(logGroups)' \
  --output text
```

### ECS execution role

```bash
aws iam list-roles \
  --query "length(Roles[?RoleName=='${PROJECT_PREFIX}-ecs-task-execution'])" \
  --output text
```

### RDS-managed secret

```bash
aws secretsmanager list-secrets \
  --region "$AWS_REGION" \
  --include-planned-deletion \
  --filters "Key=name,Values=$DATABASE_SECRET_NAME" \
  --query 'SecretList[].{
    Name:Name,
    DeletedDate:DeletedDate
  }' \
  --output json
```

Expected result when deletion is complete:

```json
[]
```

An entry containing `DeletedDate` means Secrets Manager has scheduled the
secret for deletion but has not yet removed its metadata. Record that state and
check it again later rather than treating it as an active credential.

In interactive Bash, literal values containing `!` should be enclosed in
single quotes to avoid history expansion. Values already stored through quoted
variable expansion, as above, do not require manual re-entry.

### Tagged-resource index

```bash
aws resourcegroupstaggingapi get-resources \
  --region "$AWS_REGION" \
  --tag-filters \
    "Key=Project,Values=$PROJECT_NAME" \
    "Key=Environment,Values=$ENVIRONMENT" \
  --query 'ResourceTagMappingList[].ResourceARN' \
  --output json
```

The tagging API can temporarily return stale records for deleted resources.
Verify questionable entries with service-specific APIs.

For example:

```bash
aws ec2 describe-subnets \
  --region "$AWS_REGION" \
  --subnet-ids "<subnet-id>"
```

`InvalidSubnetID.NotFound` confirms that the subnet no longer exists.

Do not manually delete resources based only on a stale tagging record.

## 12. Remove inactive ECS task-definition revisions

Terraform deregisters obsolete task-definition revisions. Inactive revisions
may remain visible after the environment is destroyed.

List them:

```bash
aws ecs list-task-definitions \
  --region "$AWS_REGION" \
  --family-prefix "$PROJECT_PREFIX-application" \
  --status INACTIVE \
  --sort ASC \
  --query 'taskDefinitionArns' \
  --output json
```

Delete all returned inactive revisions without hardcoding revision numbers:

```bash
aws ecs list-task-definitions \
  --region "$AWS_REGION" \
  --family-prefix "$PROJECT_PREFIX-application" \
  --status INACTIVE \
  --sort ASC \
  --query 'taskDefinitionArns[]' \
  --output text \
  | tr '\t' '\n' \
  | while IFS= read -r task_definition; do
      [ -n "$task_definition" ] || continue

      aws ecs delete-task-definitions \
        --region "$AWS_REGION" \
        --task-definitions "$task_definition" \
        --query '{
          deleted:taskDefinitions[].taskDefinitionArn,
          failures:failures
        }' \
        --output json
    done
```

Verify the result:

```bash
aws ecs list-task-definitions \
  --region "$AWS_REGION" \
  --family-prefix "$PROJECT_PREFIX-application" \
  --status INACTIVE \
  --query 'taskDefinitionArns' \
  --output json
```

Expected result:

```json
[]
```

AWS metadata indexes may take a short time to reflect the deletion.

## 13. Remove local artifacts

Remove saved plans and temporary responses:

```bash
rm -f \
  terraform/bootstrap.tfplan \
  terraform/deploy.tfplan \
  terraform/destroy.tfplan \
  readiness-response.json
```

Remove the local Docker image tags:

```bash
docker image rm \
  "$ECR_REPOSITORY_URL:$IMAGE_TAG" \
  "$PROJECT_NAME:$IMAGE_TAG" \
  2>/dev/null || true
```

Remove the stored ECR login:

```bash
docker logout "$ECR_REGISTRY"
```

Verify the working tree:

```bash
git status --short
```

Expected result: no output.

The remote Terraform backend is managed separately and remains available for
future verification runs.

## Troubleshooting

### Bootstrap task cannot pull its image

An image-pull error mentioning `bootstrap-placeholder` is expected before the
application image is pushed to ECR.

Continue with the image build, push, image-tag update, and second Terraform
apply.

### Git Bash rewrites the CloudWatch log-group name

Git Bash may convert `/ecs/...` into a Windows path before passing it to
`aws.exe`.

Prefix AWS Logs commands with:

```bash
MSYS2_ARG_CONV_EXCL='*'
```

This setting is not needed in Linux Bash, macOS Bash, WSL, or PowerShell.

### Bash reports `event not found`

Interactive Bash treats `!` as history expansion.

Use single quotes when manually assigning literal values containing `!`:

```bash
EXAMPLE='value!with-exclamation-mark'
```

Do not place real secret names in committed examples.

### AWS CLI reports `Unknown token ?`

Keep JMESPath filters such as `[?...]` on one line. Do not split `[` and `?`
across lines.

### ECS is stable but the ALB returns HTTP 502

ECS service stability does not guarantee that the target has completed ALB
health checks.

Wait for the target separately:

```bash
aws elbv2 wait target-in-service \
  --region "$AWS_REGION" \
  --target-group-arn "$TARGET_GROUP_ARN"
```

A temporary HTTP 502 can occur while the old target is draining and the
replacement target is becoming healthy.

### Terraform apply fails partway through

Do not delete the Terraform state or rerun commands blindly.

Inspect:

```bash
terraform -chdir=terraform state list
terraform -chdir=terraform plan
```

Determine which resources exist and create a reviewed recovery or destruction
plan.

## Historical verification record

The commands above always use the currently checked-out commit and the region
configured in `terraform/terraform.tfvars`.

The following table records completed historical runs and is not used as
procedure input:

| Date | Commit | Region | Result |
|---|---|---|---|
| 2026-07-18 | `5b69c36` | `eu-central-1` | Passed |

The 2026-07-18 run verified:

- two-stage Terraform and ECR bootstrap;
- ECS Fargate behind an Application Load Balancer;
- RDS PostgreSQL connectivity and Flyway migration V1;
- persistence across ECS task replacement;
- CloudWatch application logging;
- ALB-to-ECS-to-RDS security-group isolation;
- zero Terraform drift before destruction;
- destruction of all 34 Terraform-managed resources;
- zero remaining Terraform state entries;
- direct AWS-side cleanup;
- deletion of inactive ECS task-definition revisions.

Future verification runs should add a new table row rather than modifying the
reusable commands to contain a specific commit or region.
