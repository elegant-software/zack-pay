import * as cdk from 'aws-cdk-lib';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

interface AppStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  cluster: ecs.Cluster;
  repository: ecr.Repository;
  db: rds.DatabaseInstance;
  storageBucket: s3.Bucket;
  stripeSecret: secretsmanager.Secret;
  braintreeSecret: secretsmanager.Secret;
  keycloakSecret: secretsmanager.Secret;
  expoSecret: secretsmanager.Secret;
}

export class AppStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: AppStackProps) {
    super(scope, id, props);

    const taskRole = new iam.Role(this, 'TaskRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    // S3: scoped to the storage bucket only
    props.storageBucket.grantReadWrite(taskRole);

    // Secrets Manager: scoped to /zackpay/* only
    taskRole.addToPolicy(new iam.PolicyStatement({
      actions: ['secretsmanager:GetSecretValue'],
      resources: [
        props.stripeSecret.secretArn,
        props.braintreeSecret.secretArn,
        props.keycloakSecret.secretArn,
        props.expoSecret.secretArn,
      ],
    }));

    const taskDef = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      memoryLimitMiB: 2048,
      cpu: 512,
      taskRole,
    });

    const dbSecret = props.db.secret!;

    taskDef.addContainer('App', {
      image: ecs.ContainerImage.fromEcrRepository(props.repository, 'latest'),
      portMappings: [{ containerPort: 8080 }],
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'zackpay' }),
      environment: {
        SPRING_PROFILES_ACTIVE: 'aws-staging',
        STORAGE_BUCKET: props.storageBucket.bucketName,
        SPRING_DATASOURCE_URL: `jdbc:postgresql://${props.db.dbInstanceEndpointAddress}:5432/zackpay`,
        KEYCLOAK_IAM_SERVER_URL: 'https://auth.elegantsoftware.de',
        KEYCLOAK_IAM_REALM: 'blitzpay',
      },
      secrets: {
        SPRING_DATASOURCE_USERNAME: ecs.Secret.fromSecretsManager(dbSecret, 'username'),
        SPRING_DATASOURCE_PASSWORD: ecs.Secret.fromSecretsManager(dbSecret, 'password'),
        STRIPE_SECRET_KEY: ecs.Secret.fromSecretsManager(props.stripeSecret, 'stripe.secret-key'),
        STRIPE_WEBHOOK_SECRET: ecs.Secret.fromSecretsManager(props.stripeSecret, 'stripe.webhook-secret'),
        STRIPE_CONNECT_WEBHOOK_SECRET: ecs.Secret.fromSecretsManager(props.stripeSecret, 'stripe.connect-webhook-secret'),
        BRAINTREE_PRIVATE_KEY: ecs.Secret.fromSecretsManager(props.braintreeSecret, 'braintree.private-key'),
        KEYCLOAK_IAM_ADMIN_CLIENT_ID: ecs.Secret.fromSecretsManager(props.keycloakSecret, 'keycloak.iam.admin-client-id'),
        KEYCLOAK_IAM_ADMIN_CLIENT_SECRET: ecs.Secret.fromSecretsManager(props.keycloakSecret, 'keycloak.iam.admin-client-secret'),
        EXPO_ACCESS_TOKEN: ecs.Secret.fromSecretsManager(props.expoSecret, 'zackpay.expo.access-token'),
      },
    });

    const sg = new ec2.SecurityGroup(this, 'AppSg', { vpc: props.vpc });

    const service = new ecs.FargateService(this, 'Service', {
      cluster: props.cluster,
      taskDefinition: taskDef,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      securityGroups: [sg],
      circuitBreaker: { rollback: true },
      healthCheckGracePeriod: cdk.Duration.seconds(300),
    });

    const cert = new acm.Certificate(this, 'Cert', {
      domainName: 'api.elegantsoftware.de',
      validation: acm.CertificateValidation.fromDns(),
    });

    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      vpc: props.vpc,
      internetFacing: true,
    });

    alb.addListener('Http', { port: 80 }).addAction('RedirectToHttps', {
      action: elbv2.ListenerAction.redirect({ protocol: 'HTTPS', port: '443', permanent: true }),
    });

    const listener = alb.addListener('Https', {
      port: 443,
      certificates: [cert],
    });
    listener.addTargets('App', {
      port: 8080,
      targets: [service],
      healthCheck: { path: '/actuator/health' },
    });

    // Allow ALB → app; restrict direct internet access to Fargate tasks
    sg.addIngressRule(
      ec2.Peer.securityGroupId(alb.connections.securityGroups[0].securityGroupId),
      ec2.Port.tcp(8080),
    );

    new cdk.CfnOutput(this, 'AppUrl', { value: 'https://api.elegantsoftware.de' });
    new cdk.CfnOutput(this, 'AlbDns', { value: alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'CertArn', { value: cert.certificateArn });
  }
}
