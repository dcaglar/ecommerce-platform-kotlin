# Docker Management Scripts

This directory contains helper scripts to manage your modular eCommerce infrastructure using Docker Compose.

## Scripts Overview

| Script              | Description                                                                |
|---------------------|----------------------------------------------------------------------------|
| `network-up.sh`     | Create required Docker networks (idempotent)                               |
| `infra-up.sh`       | Start infrastructure containers                                            |
| `infra-down.sh`     | Stop infra containers (keep data/volumes)                                  |
| `infra-cleanup.sh`  | Stop and remove infra containers + volumes (with confirmation)             |
| `infra-purge.sh`    | **Dangerous**: remove all infra containers and volumes (with confirmation) |
| `app-up.sh`         | Start app containers with optional profile (`./app-up.sh docker`)          |
| `app-down.sh`       | Stop app containers                                                        |
| `compose-up.sh`     | Bring up full stack (network + infra + app)                                |
| `compose-down.sh`   | Bring down full stack (app + infra)                                        |
| `logs.sh`           | Tail logs from payment-service container                                   |
| `status.sh`         | Show status of app and infra containers                                    |
| `scale-consumer.sh` | Scale the number of payment-consumers service instances up or down         |

## Scaling payment-consumers

You can scale the payment-consumers service (for Kafka partition parallelism) using the `scale-consumer.sh` script:

- **Set a specific number of instances (e.g., 8):**
  ```sh
  ./scale-consumer.sh 8
  ```
- **Increase the number of instances by 1:**
  ```sh
  ./scale-consumer.sh --up
  ```
- **Decrease the number of instances by 1:**
  ```sh
  ./scale-consumer.sh --down
  ```
- **Show usage instructions:**
  ```sh
  ./scale-consumer.sh --help
  ```

> Note: Scaling works only if the `container_name` and `ports` sections are not set for payment-consumers in your
> docker-compose.app.yml.

## Typical Workflow

### Start Everything (Clean Start)

```bash
./network-up.sh
./infra-up.sh
./app-up.sh docker
```

### Stop Everything (Keep Data)

```bash
./app-down.sh
./infra-down.sh
```

### Full Reset (Lose All Data & Config)

```bash
./app-down.sh
./infra-down.sh
./infra-cleanup.sh   # or ./infra-purge.sh
./network-up.sh      # (optional, if you want to recreate networks)
./infra-up.sh
./app-up.sh
```

> **Warning:** `infra-cleanup.sh` and `infra-purge.sh` will delete all persistent data and config for infra containers.
> Use with caution!

### Check Status

```bash
./status.sh
```

### Tail Logs

```bash
./logs.sh [container-name]
```

## Notes

- All scripts check if Docker is running and provide help output (`-h` or `--help`).
- Use named Docker volumes for persistence. Data is only lost if you run cleanup/purge scripts.
- For structured logging and observability, see `logback-spring.xml` and `filebeat.yml` in the respective service/config
  folders.
- For Grafana provisioning, see `prometheus/grafana-provisioning/`.

---

For more details, see the main project README or the documentation in the `docs/` folder.
./in

const secretsFile = open('./secrets.txt');

docker run --rm -i \
--network auth-net \
-v "$PWD/load-tests:/scripts" \
-v "$PWD/keycloak/secrets.txt:/scripts/secrets.txt" \
grafana/k6 run /scripts/baseline-smoke-test.js