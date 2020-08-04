# TP Arquitecturas concurrentes

### Construir ejecutable

Iniciar consola de sbt en el directorio del proyecto
```bash
sbt
```
Compilar el mismo
```bash
compile
```

Finalmente ejecutar el dist
```bash
dist
```
El dist generará un zip en el directorio 'tp-arq-concurrentes\target\universal\'


### Cómo ejecutar el cluster desde el ejecutable ya construido:
Ubicarse en la carpeta '/ejecutable/bin' y ejecutar alguno de los siguientes comandos, previamente descomprimiendo el zip:

Primero levantar el ejecutable con un único parámetro (Esto ejecuta todos los actores menos el auction-spawner en un único proceso)
```bash
tp-arq-concurrentes main
```

Finalmente levantar una instancia del auction-spawner:
```bash
tp-arq-concurrentes auction-spawner 0
```

### Cómo ejecutar el cluster usando SBT: 
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

### Comandos útiles para pruebas

#### Windows (CMD)

Para determinar qué proceso está escuchando en un puerto y obtener su PID
```bash
netstat -ano | findstr /r "LISTENING.*<Puerto>"
```

Para finalizar un proceso a partir de su PID
```bash
taskkill /F /PID <Puerto>
```