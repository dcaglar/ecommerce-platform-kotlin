# Docker Management Scripts

This directory contains helper scripts to manage your modular eCommerce infrastructure.

## Scripts

| Script            | Description |
|------------------|-------------|
| `network-up.sh`  | Create required Docker networks |
| `infra-up.sh`    | Start infrastructure containers |
| `infra-down.sh`  | Stop infra containers (keep data) |
| `infra-cleanup.sh` | Stop and remove infra containers + volumes |
| `infra-purge.sh` | **Dangerous**: remove all infra containers and volumes |
| `app-up.sh`      | Start app containers with optional profile (`./app-up.sh docker`) |
| `app-down.sh`    | Stop app containers |
| `compose-up.sh`  | Bring up full stack (network + infra + app) |
| `compose-down.sh`| Bring down full stack (app + infra) |
| `logs.sh`        | Tail logs from payment-service container |

## Example Usage

```bash
./network-up.sh
./infra-up.sh
./app-up.sh docker
./logs.sh
```

Use `infra-cleanup.sh` or `infra-purge.sh` cautiously.
