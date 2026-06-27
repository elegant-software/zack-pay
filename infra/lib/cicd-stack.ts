import * as cdk from 'aws-cdk-lib';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as s3 from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';

interface CiCdStackProps extends cdk.StackProps {
  repository: ecr.Repository;
  /** GitHub org/repos allowed to assume the deploy role */
  githubRepos: string[];
  webAdminBucket: s3.Bucket;
  /** CloudFront distribution ID for cache invalidation after deploy */
  webAdminDistributionId: string;
}

export class CiCdStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: CiCdStackProps) {
    super(scope, id, props);

    const provider = new iam.OpenIdConnectProvider(this, 'GitHubOidc', {
      url: 'https://token.actions.githubusercontent.com',
      clientIds: ['sts.amazonaws.com'],
    });

    const deployRole = new iam.Role(this, 'GitHubDeployRole', {
      roleName: 'zackpay-github-deploy',
      assumedBy: new iam.WebIdentityPrincipal(provider.openIdConnectProviderArn, {
        StringEquals: {
          'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
        },
        StringLike: {
          'token.actions.githubusercontent.com:sub': props.githubRepos.map(r => `repo:${r}:*`),
        },
      }),
      maxSessionDuration: cdk.Duration.hours(1),
    });

    // ECR: push/pull images
    props.repository.grantPullPush(deployRole);

    // ECS: deploy new task definitions and update services
    deployRole.addToPolicy(new iam.PolicyStatement({
      actions: [
        'ecs:DescribeServices',
        'ecs:UpdateService',
        'ecs:ListServices',
        'ecs:DescribeTaskDefinition',
        'ecs:RegisterTaskDefinition',
        'ecs:DeregisterTaskDefinition',
        'ecs:TagResource',
      ],
      resources: ['*'],
      conditions: {
        // ponytail: broad resource — scope to cluster ARN if multi-cluster
      },
    }));

    // Pass task/execution roles to ECS
    deployRole.addToPolicy(new iam.PolicyStatement({
      actions: ['iam:PassRole'],
      resources: ['*'],
      conditions: {
        StringEquals: {
          'iam:PassedToService': 'ecs-tasks.amazonaws.com',
        },
      },
    }));

    // Web admin: sync built Angular dist/ to S3 then invalidate CloudFront
    props.webAdminBucket.grantReadWrite(deployRole);
    deployRole.addToPolicy(new iam.PolicyStatement({
      actions: ['cloudfront:CreateInvalidation'],
      resources: [`arn:aws:cloudfront::${this.account}:distribution/${props.webAdminDistributionId}`],
    }));

    new cdk.CfnOutput(this, 'DeployRoleArn', {
      value: deployRole.roleArn,
      description: 'Set this as AWS_DEPLOY_ROLE_ARN in GitHub repo secrets',
    });
    new cdk.CfnOutput(this, 'WebAdminBucket', {
      value: props.webAdminBucket.bucketName,
      description: 'Set this as WEB_ADMIN_BUCKET in GitHub repo secrets',
    });
    new cdk.CfnOutput(this, 'WebAdminDistributionId', {
      value: props.webAdminDistributionId,
      description: 'Set this as WEB_ADMIN_DISTRIBUTION_ID in GitHub repo secrets',
    });
  }
}
