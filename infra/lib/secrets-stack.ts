import * as cdk from 'aws-cdk-lib';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export class SecretsStack extends cdk.Stack {
  readonly stripeSecret: secretsmanager.Secret;
  readonly braintreeSecret: secretsmanager.Secret;
  readonly keycloakSecret: secretsmanager.Secret;
  readonly expoSecret: secretsmanager.Secret;

  constructor(scope: Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props);

    this.stripeSecret = new secretsmanager.Secret(this, 'StripeSecret', {
      secretName: '/blitzpay/stripe',
      description: 'Stripe secret key, webhook secrets',
      secretObjectValue: {
        'stripe.secret-key': cdk.SecretValue.unsafePlainText('REPLACE_ME'),
        'stripe.webhook-secret': cdk.SecretValue.unsafePlainText('REPLACE_ME'),
        'stripe.connect-webhook-secret': cdk.SecretValue.unsafePlainText('REPLACE_ME'),
      },
    });

    this.braintreeSecret = new secretsmanager.Secret(this, 'BraintreeSecret', {
      secretName: '/blitzpay/braintree',
      description: 'Braintree private key',
      secretObjectValue: {
        'braintree.private-key': cdk.SecretValue.unsafePlainText('REPLACE_ME'),
      },
    });

    this.keycloakSecret = new secretsmanager.Secret(this, 'KeycloakSecret', {
      secretName: '/blitzpay/keycloak',
      description: 'Keycloak admin service account credentials',
      secretObjectValue: {
        'keycloak.iam.admin-client-id': cdk.SecretValue.unsafePlainText('REPLACE_ME'),
        'keycloak.iam.admin-client-secret': cdk.SecretValue.unsafePlainText('REPLACE_ME'),
      },
    });

    this.expoSecret = new secretsmanager.Secret(this, 'ExpoSecret', {
      secretName: '/blitzpay/expo',
      description: 'Expo Push API access token',
      secretObjectValue: {
        'blitzpay.expo.access-token': cdk.SecretValue.unsafePlainText('REPLACE_ME'),
      },
    });
  }
}
