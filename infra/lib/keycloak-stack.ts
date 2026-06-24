import * as cdk from 'aws-cdk-lib';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

interface KeycloakStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  cluster: ecs.Cluster;
  db: rds.DatabaseInstance;
  keycloakAdminSecret: secretsmanager.Secret;
}

export class KeycloakStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: KeycloakStackProps) {
    super(scope, id, props);

    const taskDef = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      memoryLimitMiB: 2048,
      cpu: 1024,
    });

    const dbSecret = props.db.secret!;

    taskDef.addContainer('Keycloak', {
      image: ecs.ContainerImage.fromRegistry('quay.io/keycloak/keycloak:26.6.3'),
      command: ['start'],
      portMappings: [{ containerPort: 8080 }],
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'keycloak' }),
      environment: {
        KC_DB: 'postgres',
        KC_DB_URL: `jdbc:postgresql://${props.db.dbInstanceEndpointAddress}:5432/keycloak`,
        KC_PROXY_HEADERS: 'xforwarded',
        KC_HTTP_ENABLED: 'true',
        KC_HOSTNAME: 'https://auth.elegantsoftware.de',
        // ponytail: single node — skip JGroups clustering (saves ~20s stale JDBC_PING discovery)
        KC_CACHE: 'local',
        KC_HEALTH_ENABLED: 'true',
      },
      secrets: {
        KC_DB_USERNAME: ecs.Secret.fromSecretsManager(dbSecret, 'username'),
        KC_DB_PASSWORD: ecs.Secret.fromSecretsManager(dbSecret, 'password'),
        KEYCLOAK_ADMIN: ecs.Secret.fromSecretsManager(props.keycloakAdminSecret, 'username'),
        KEYCLOAK_ADMIN_PASSWORD: ecs.Secret.fromSecretsManager(props.keycloakAdminSecret, 'password'),
      },
    });

    const sg = new ec2.SecurityGroup(this, 'KeycloakSg', { vpc: props.vpc });

    const service = new ecs.FargateService(this, 'Service', {
      cluster: props.cluster,
      taskDefinition: taskDef,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      securityGroups: [sg],
      circuitBreaker: { rollback: true },
      healthCheckGracePeriod: cdk.Duration.seconds(180),
    });

    const cert = new acm.Certificate(this, 'Cert', {
      domainName: 'auth.elegantsoftware.de',
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
    listener.addTargets('Keycloak', {
      port: 8080,
      targets: [service],
      healthCheck: {
        path: '/realms/master',
        healthyHttpCodes: '200',
      },
    });

    sg.addIngressRule(
      ec2.Peer.securityGroupId(alb.connections.securityGroups[0].securityGroupId),
      ec2.Port.tcp(8080),
    );

    new cdk.CfnOutput(this, 'KeycloakUrl', { value: `https://auth.elegantsoftware.de` });
    new cdk.CfnOutput(this, 'AlbDns', { value: alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'CertArn', { value: cert.certificateArn });
  }
}
