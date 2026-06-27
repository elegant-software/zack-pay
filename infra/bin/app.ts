#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/network-stack';
import { DataStack } from '../lib/data-stack';
import { SecretsStack } from '../lib/secrets-stack';
import { EcrStack } from '../lib/ecr-stack';
import { AppStack } from '../lib/app-stack';
import { BastionStack } from '../lib/bastion-stack';
import { KeycloakStack } from '../lib/keycloak-stack';
import { CiCdStack } from '../lib/cicd-stack';
import { WebAdminStack } from '../lib/webadmin-stack';

const app = new cdk.App();

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: 'eu-central-1',
};

const network = new NetworkStack(app, 'ZackPayNetwork', { env });
const data = new DataStack(app, 'ZackPayData', { env, vpc: network.vpc });
const secrets = new SecretsStack(app, 'ZackPaySecrets', { env });
const ecr = new EcrStack(app, 'ZackPayEcr', { env });

new BastionStack(app, 'ZackPayBastion', { env, vpc: network.vpc });

new KeycloakStack(app, 'ZackPayKeycloak', {
  env,
  vpc: network.vpc,
  cluster: network.cluster,
  db: data.db,
  keycloakAdminSecret: secrets.keycloakAdminSecret,
});

new AppStack(app, 'ZackPayApp', {
  env,
  vpc: network.vpc,
  cluster: network.cluster,
  repository: ecr.repository,
  db: data.db,
  storageBucket: data.storageBucket,
  stripeSecret: secrets.stripeSecret,
  braintreeSecret: secrets.braintreeSecret,
  keycloakSecret: secrets.keycloakSecret,
  expoSecret: secrets.expoSecret,
});

const webAdmin = new WebAdminStack(app, 'ZackPayWebAdmin', { env });

const cicd = new CiCdStack(app, 'ZackPayCiCd', {
  env,
  repository: ecr.repository,
  githubRepos: ['elegant-software/blitz-pay', 'elegant-software/blitzpay-admin-dashboard'],
  webAdminBucket: webAdmin.bucket,
  webAdminDistributionId: webAdmin.distribution.distributionId,
});
