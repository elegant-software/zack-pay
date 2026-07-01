import * as cdk from 'aws-cdk-lib';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';

interface DashboardStackProps extends cdk.StackProps {
  alb: elbv2.ApplicationLoadBalancer;
  service: ecs.FargateService;
  appLogGroup: logs.LogGroup;
}

export class DashboardStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: DashboardStackProps) {
    super(scope, id, props);

    const { alb, service, appLogGroup } = props;
    const m1 = cdk.Duration.minutes(1);

    new cloudwatch.Dashboard(this, 'ApiDashboard', {
      dashboardName: 'ZackPay-API',
      widgets: [
        [
          new cloudwatch.GraphWidget({
            title: 'Request Rate',
            left: [alb.metricRequestCount({ period: m1, statistic: 'Sum', label: 'Requests/min' })],
            width: 8,
          }),
          new cloudwatch.GraphWidget({
            title: '5xx Errors',
            left: [
              alb.metricHttpCodeElb(elbv2.HttpCodeElb.ELB_5XX_COUNT, { period: m1, statistic: 'Sum', label: 'ALB 5xx' }),
              alb.metricHttpCodeTarget(elbv2.HttpCodeTarget.TARGET_5XX_COUNT, { period: m1, statistic: 'Sum', label: 'App 5xx' }),
            ],
            width: 8,
          }),
          new cloudwatch.GraphWidget({
            title: 'Latency (p50 / p99)',
            left: [
              alb.metricTargetResponseTime({ period: m1, statistic: 'p50', label: 'p50' }),
              alb.metricTargetResponseTime({ period: m1, statistic: 'p99', label: 'p99' }),
            ],
            width: 8,
          }),
        ],
        [
          new cloudwatch.GraphWidget({
            title: 'ECS CPU %',
            left: [service.metricCpuUtilization({ period: m1, label: 'CPU %' })],
            width: 12,
          }),
          new cloudwatch.GraphWidget({
            title: 'ECS Memory %',
            left: [service.metricMemoryUtilization({ period: m1, label: 'Memory %' })],
            width: 12,
          }),
        ],
        [
          new cloudwatch.LogQueryWidget({
            title: 'API Errors (last 1h)',
            logGroupNames: [appLogGroup.logGroupName],
            queryLines: [
              'fields @timestamp, @message',
              '| filter @message like /ERROR|Exception|5[0-9][0-9]/',
              '| sort @timestamp desc',
              '| limit 100',
            ],
            width: 24,
            height: 8,
          }),
        ],
      ],
    });
  }
}
