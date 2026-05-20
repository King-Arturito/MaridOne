# Backend Auto Deploy

This repo deploys automatically when changes are pushed to `master`.

The GitHub Actions workflow builds the Spring Boot Docker image, pushes it to ECR, creates a new ECS task definition revision with that image, and updates the ECS service.

## Current AWS Targets

- AWS account: `379264687366`
- Region: `us-east-1`
- ECR repository: `maridone-backend`
- ECS cluster: `maridone-api-cluster`
- ECS service: `maridone-backend-task-service`
- ECS task family: `maridone-backend-task`
- Container name: `backend-container`
- Load balancer: `maridone-lb`
- Target group: `maridone-backend-tg`
- CloudFront distribution using backend origin: `E2EQ71U8FR05ED`

The workflow downloads the current ECS task definition during each deploy and only changes the container image. Existing CPU, memory, environment variables, SSM secrets, roles, logging, and networking are preserved.

## IAM

No long-lived AWS access key is stored in GitHub. The workflow assumes this AWS role through OIDC:

```text
arn:aws:iam::379264687366:role/MaridOneBackendGitHubDeployRole
```

The role trust policy is scoped to:

```text
repo:King-Arturito/MaridOne:environment:production
```

## Team Workflow

Groupmates should work on branches and open pull requests into `master`.

After a PR is merged into `master`, GitHub Actions deploys the updated backend automatically. Direct pushes to `master` also deploy automatically, but pull requests are safer because tests and builds can run before changes land.
