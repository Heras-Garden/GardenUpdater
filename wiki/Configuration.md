# Configuration

The managed plugin list maps a Bukkit plugin name to a GitHub repository and expected release asset.

Example:

```yaml
plugins:
  GardenCore:
    repository: Heras-Garden/GardenCore
    asset: GardenCore.jar
  GardenLands:
    repository: Heras-Garden/GardenLands
    asset: GardenLands.jar
```

Recommended defaults:

- Check on startup
- Check periodically
- Do not automatically restart the server
- Do not install pre-releases unless explicitly enabled
- Use GitHub's public release API without a token when possible