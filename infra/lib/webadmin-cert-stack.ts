import * as cdk from 'aws-cdk-lib';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';

// CloudFront requires certs in us-east-1 — must be a separate stack.
export class WebAdminCertStack extends cdk.Stack {
  readonly cert: acm.Certificate;

  constructor(scope: Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.cert = new acm.Certificate(this, 'Cert', {
      domainName: 'admin.zackpay.de',
      validation: acm.CertificateValidation.fromDns(),
    });
  }
}
