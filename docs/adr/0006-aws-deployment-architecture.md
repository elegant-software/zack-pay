# ADR-0006: AWS deployment architecture

**Date:** 2026-06-02
**Status:** Accepted (in progress — domain name and CI/CD approval gate still open)

## ECS Fargate over AWS Lambda

The backend is deployed on ECS Fargate, not Lambda. Lambda was ruled out for two reasons: `QrPaymentSseController` holds SSE connections open for up to 5 minutes, which exceeds Lambda's request/response model without significant rearchitecting; and the Spring Boot + JPA + Liquibase + Spring Modulith stack incurs 10–30 second cold starts that would make Lambda unusable for interactive mobile clients.

## ECS Fargate over EKS

ECS Fargate was chosen over EKS. EKS charges $72/month for the control plane before a single container runs. With one backend service and Keycloak, that cost is not amortised. EKS becomes the right call when 10+ services share a cluster across multiple teams.

## Public subnets for Fargate tasks (no NAT Gateway)

Fargate tasks run in public subnets with public IPs assigned. RDS remains in private subnets with no internet access. A NAT Gateway would cost ~$32/month and exists only to give private-subnet tasks outbound internet access (TrueLayer, Stripe, Expo Push). Security groups lock down all inbound traffic to the ALB only — the public IP is for outbound, not inbound exposure. Revisit when compliance requirements demand private subnets.

## Shared RDS instance for BlitzPay and Keycloak

BlitzPay and Keycloak share one RDS `db.t3.micro` instance with separate PostgreSQL databases (`blitzpay` and `keycloak`) and separate database users. A second RDS instance for Keycloak alone costs $13–26/month with no operational benefit at current scale. Upgrade to `db.t3.small` if connection pressure becomes visible.

## CDK TypeScript for infrastructure

Infrastructure is defined in AWS CDK using TypeScript, not Java or Terraform. CDK was chosen over Terraform because the deployment is AWS-only and CDK's L2 constructs for ECS, RDS, and VPC reduce boilerplate significantly. TypeScript was chosen over Java CDK because CDK was written in TypeScript — all documentation, examples, and community support are TypeScript-first; the Java bindings are auto-generated and noticeably more verbose.

## Open decisions

- **Domain name** for the backend API (needed before any mobile app release — mobile clients embed the URL and cannot be force-updated)
- **CI/CD prod approval gate** — automatic deploy on push to main vs manual approval step before ECS service update
