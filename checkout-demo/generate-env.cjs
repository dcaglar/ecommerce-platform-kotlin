#!/usr/bin/env node

/**
 * Helper script to generate .env file from Keycloak secrets
 * Reads from keycloak/output/secrets.txt and creates/updates .env
 */

const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '..');
const SECRETS_FILE = path.join(PROJECT_ROOT, 'keycloak', 'output', 'secrets.txt');
const ENV_FILE = path.join(__dirname, '.env');
const ENV_EXAMPLE = path.join(__dirname, '.env.example');

// Default values
const defaults = {
  VITE_KEYCLOAK_URL: 'http://127.0.0.1:8080',
  VITE_KEYCLOAK_REALM: 'ecommerce-platform',
  VITE_KEYCLOAK_CLIENT_ID: 'payment-service',
  VITE_KEYCLOAK_CLIENT_SECRET: '',
  VITE_API_BASE_URL: 'http://127.0.0.1',
  VITE_API_HOST_HEADER: 'payment.192.168.49.2.nip.io',
  VITE_STRIPE_PUBLISHABLE_KEY: 'pk_test_placeholder' // ⚠️ MUST BE REPLACED with your actual Stripe publishable key
};

function readSecrets() {
  if (!fs.existsSync(SECRETS_FILE)) {
    console.error(`❌ Secrets file not found: ${SECRETS_FILE}`);
    console.error('💡 Please run ./keycloak/provision-keycloak.sh first');
    process.exit(1);
  }

  const content = fs.readFileSync(SECRETS_FILE, 'utf8');
  const secrets = {};
  
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    
    const match = trimmed.match(/^([^=]+)=(.*)$/);
    if (match) {
      const key = match[1].trim();
      const value = match[2].trim();
      secrets[key] = value;
    }
  }

  return secrets;
}

function readEndpoints() {
  const endpointsFile = path.join(PROJECT_ROOT, 'infra', 'endpoints.json');
  if (!fs.existsSync(endpointsFile)) {
    return null;
  }

  try {
    const content = fs.readFileSync(endpointsFile, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.warn(`⚠️  Could not read endpoints.json: ${error.message}`);
    return null;
  }
}

function readExistingEnv() {
  const env = {};
  if (fs.existsSync(ENV_FILE)) {
    const content = fs.readFileSync(ENV_FILE, 'utf8');
    for (const line of content.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      
      const match = trimmed.match(/^([^=]+)=(.*)$/);
      if (match) {
        const key = match[1].trim();
        const value = match[2].trim();
        env[key] = value;
      }
    }
  }
  return env;
}

function generateEnv() {
  console.log('🔧 Generating .env file...\n');

  // Read secrets
  const secrets = readSecrets();
  const clientSecret = secrets.PAYMENT_SERVICE_CLIENT_SECRET;
  
  if (!clientSecret) {
    console.error('❌ PAYMENT_SERVICE_CLIENT_SECRET not found in secrets file');
    process.exit(1);
  }

  // Read endpoints if available
  const endpoints = readEndpoints();

  // Read existing .env to preserve custom values
  const existing = readExistingEnv();

  // Build env object
  const env = {
    ...defaults,
    ...existing, // Preserve existing custom values (including VITE_STRIPE_PUBLISHABLE_KEY if already set)
    VITE_KEYCLOAK_CLIENT_SECRET: clientSecret,
    // Always use correct client ID (override any existing wrong value)
    VITE_KEYCLOAK_CLIENT_ID: defaults.VITE_KEYCLOAK_CLIENT_ID,
  };
  
  // Preserve Stripe publishable key if it was set in existing env (don't override with placeholder)
  if (existing.VITE_STRIPE_PUBLISHABLE_KEY && existing.VITE_STRIPE_PUBLISHABLE_KEY !== defaults.VITE_STRIPE_PUBLISHABLE_KEY) {
    env.VITE_STRIPE_PUBLISHABLE_KEY = existing.VITE_STRIPE_PUBLISHABLE_KEY;
  }

  // Override with endpoints.json if available
  if (endpoints) {
    if (endpoints.base_url) {
      env.VITE_API_BASE_URL = endpoints.base_url;
    }
    if (endpoints.host_header) {
      env.VITE_API_HOST_HEADER = endpoints.host_header;
    }
  }

  // Generate .env content
  const lines = [
    '# Keycloak Configuration',
    `VITE_KEYCLOAK_URL=${env.VITE_KEYCLOAK_URL}`,
    `VITE_KEYCLOAK_REALM=${env.VITE_KEYCLOAK_REALM}`,
    `VITE_KEYCLOAK_CLIENT_ID=${env.VITE_KEYCLOAK_CLIENT_ID}`,
    `VITE_KEYCLOAK_CLIENT_SECRET=${env.VITE_KEYCLOAK_CLIENT_SECRET}`,
    '',
    '# Payment API Configuration',
    '# These can be read from infra/endpoints.json, but can be overridden here',
    `VITE_API_BASE_URL=${env.VITE_API_BASE_URL}`,
    `VITE_API_HOST_HEADER=${env.VITE_API_HOST_HEADER}`,
    '',
    '# Stripe Configuration',
    '# ⚠️ IMPORTANT: Replace pk_test_placeholder with your actual Stripe publishable key',
    '# Get your publishable key from: https://dashboard.stripe.com/test/apikeys',
    '# Test keys start with pk_test_, Live keys start with pk_live_',
    `VITE_STRIPE_PUBLISHABLE_KEY=${env.VITE_STRIPE_PUBLISHABLE_KEY}`,
    ''
  ];

  // Write .env file
  fs.writeFileSync(ENV_FILE, lines.join('\n'), 'utf8');

  console.log('✅ .env file generated successfully!');
  console.log(`   Location: ${ENV_FILE}\n`);
  console.log('📋 Configuration:');
  console.log(`   Keycloak URL: ${env.VITE_KEYCLOAK_URL}`);
  console.log(`   Realm: ${env.VITE_KEYCLOAK_REALM}`);
  console.log(`   Client ID: ${env.VITE_KEYCLOAK_CLIENT_ID}`);
  console.log(`   Client Secret: ${clientSecret.substring(0, 8)}... (hidden)`);
  console.log(`   API Base URL: ${env.VITE_API_BASE_URL}`);
  console.log(`   API Host Header: ${env.VITE_API_HOST_HEADER}`);
  console.log(`   Stripe Publishable Key: ${env.VITE_STRIPE_PUBLISHABLE_KEY.substring(0, 20)}...`);
  
  if (env.VITE_STRIPE_PUBLISHABLE_KEY === 'pk_test_placeholder') {
    console.log('\n⚠️  WARNING: Stripe publishable key is still set to placeholder!');
    console.log('   Please update VITE_STRIPE_PUBLISHABLE_KEY in .env with your actual Stripe key.');
    console.log('   Get it from: https://dashboard.stripe.com/test/apikeys\n');
  }
  
  console.log('💡 You can now run: npm run dev');
}

generateEnv();

