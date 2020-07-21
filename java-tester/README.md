# java-tester

Bot que al iniciar toma un port random y se registra como buyer en el cluster.

Siempre que reciba una invitación a una subasta, la registra y cada X tiempo manda un bid a la misma con un incremento aleatorio.

Expone también la API para recibir las diferentes notificaciones del cluster.

### Ejecución

Para ejecutarlo (usando gradle wrapper):
```bash
gradlew bootRun
```

Como alternativa se puede realizar un build y luego ejecutar el jar:
```bash
gradlew build
```
Nota: El jar generado se encuentra normalmente en 'build\libs'

Para ejecutarlo:
```bash
java -jar <file>.jar
```

### Parámetros opcionales

- auction.server : Host donde está el cluster / server. Default: http://localhost:8081
- client.name : Nombre a usar al registrarse. Default: 'cliente:'+ip 
- client.tags : Tags del cliente. Default: tag1,tag2
- client.bid.delay : Milisegundos de delay entre cada bid. Default: 5000
- client.bid.max-increase : Máximo incremento por bid. Default: 10.0

Para especificar la configuración se puede ejecutar como en el siguiente ejemplo:
```bash
gradlew bootRun -Pargs=--client.bid.delay=10000,--client.tags=tag1
```

O el siguiente para el caso de ejecutar el jar:
```bash
java -jar <file>.jar --client.bid.delay=5000 --client.tags=tag2
```
