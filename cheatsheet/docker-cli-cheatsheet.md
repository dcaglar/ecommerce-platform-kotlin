
# 🐳 Docker CLI Cheatsheet

A handy guide for Docker & Docker Compose commands.

---

## 🔧 Docker Commands

### 🚀 Run Containers

```bash
docker run -d --name my-container my-image
docker run -it ubuntu bash           # Interactive shell
docker run --rm my-image             # Auto-remove after exit
```

### 📦 Manage Images

```bash
docker images                        # List images
docker rmi <image_id>                # Remove image
docker build -t my-image .           # Build from Dockerfile
```

### 📂 Volumes

```bash
docker volume ls                     # List volumes
docker volume rm <volume_name>       # Delete volume
docker volume inspect <volume_name>  # Inspect volume
```

### 📁 Bind Mounts vs Volumes

```yaml
# Bind mount (host path)
volumes:
  - ./local-path:/container-path

# Named volume
volumes:
  - my_volume:/container-path
```

---

## ⚙️ Docker Compose

### 📦 Lifecycle

```bash
docker compose up -d                 # Start all services in detached mode
docker compose down                  # Stop and remove containers
docker compose down -v               # Also remove volumes
docker compose stop                  # Only stop services (not remove)
docker compose restart               # Restart services
```

### 🧪 Debug / Logs

```bash
docker compose logs -f               # Follow logs
docker compose ps                    # List running services
docker compose exec <svc> sh         # Exec into container
```

### 🔍 Health & Info

```bash
docker inspect <container>           # Detailed container info
docker stats                         # Real-time resource usage
docker system df                     # Docker disk usage
```

---

## 🧼 Cleanup

```bash
docker system prune -a               # Remove unused containers, images, volumes
docker volume prune                  # Remove unused volumes
docker container prune               # Remove stopped containers
```

---

## 🧠 Tips

- Use `docker compose config` to validate your compose file.
- Use `.env` to externalize variables in `docker-compose.yml`.
- `--network` can be used to connect multiple services manually.

