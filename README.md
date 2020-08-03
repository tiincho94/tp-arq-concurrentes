# TP Arquitecturas concurrentes

### Cómo ejecutar el cluster: 
#### Opción 1: Cada instancia en una terminal independiente:

Levantar seeds:
```bash
sbt "runMain iasc.g4.App seed 25251"
```
```bash
sbt "runMain iasc.g4.App seed 25252"
```

Diferentes actores principales:
```bash
sbt "runMain iasc.g4.App notifier-spawner 0"
```
```bash
sbt "runMain iasc.g4.App auction-spawner 0"
```
```bash
sbt "runMain iasc.g4.App buyers-suscriptor 0"
```

Finalmente el server http:
```bash
sbt "runMain iasc.g4.App http-server 0"
```

#### Opción 2: Levantar todo en una única terminal:

Como alternativa se puede ejecutar todo en una única terminal, ya sea desde el Run del IDE (Referenciando al 
archivo App); o bien desde consola:

```bash
sbt "runMain iasc.g4.App"
```
