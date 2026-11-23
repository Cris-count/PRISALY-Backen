# Orquestación de la raíz compartida — DC-OPS-71

Este directorio pertenece al Git de **PRISALY-Backen**, pero su payload se instala en la carpeta padre compartida que contiene `PRISALY-Backen` y `PRISALY-Front`. La raíz `PRISALY-ENTREGA` **no es un tercer repositorio Git**. No se agregan submódulos, enlaces versionados, clones o dependencias Git entre los dos repositorios.

No ejecutar npm up dentro del Backend ni de este paquete. Una vez instalado, los comandos se ejecutan desde la raíz compartida.

## Instalación

Requisitos: repositorios existentes, Docker Desktop Linux local, Node >=22/npm y Java/Javac 21. Maven lo gestiona el wrapper del Backend.

Desde la raíz compartida, indicando la ruta real de la versión del paquete a revisar:

```powershell
node RUTA_PAQUETE\install.mjs --root . --check
node RUTA_PAQUETE\install.mjs --root . --apply
npm run up
npm run status
npm run down
```

Si el paquete ya está en `PRISALY-Backen/ops/entrega-root`, `RUTA_PAQUETE` es esa ruta. También puede ejecutarse desde un worktree de revisión apuntando `--root` a la raíz compartida; no hace falta cambiar de rama los repositorios de integrantes.

`--check` es el comportamiento predeterminado y no escribe. `--apply` instala únicamente los **13 archivos de `payload-files.json`**, más el recibo local `.installed.json`. No copia archivos recursivamente sin lista; no toca fuentes de Backend/Frontend, runtime ni credenciales.

## Conflictos y actualización

El instalador comprueba todos los destinos antes de escribir. Archivo inexistente: crear. Archivo idéntico: conservar. Archivo distinto sin recibo o con cambio manual: **detener todo**, mostrar los conflictos y solicitar revisión/autorización al operador. No existe `--force` ni se adopta silenciosamente un archivo ajeno.

En una actualización de archivos previamente instalados, solo se reemplaza un archivo si su hash actual sigue coincidiendo con el recibo de la versión instalada. Se guarda copia previa en `.install-backups/<fecha>` y se actualiza el recibo. Una modificación manual posterior detiene la actualización antes de escribir. Se rechazan enlaces en destinos/recibo/respaldo que pudieran sacar escrituras de la raíz.

Para actualizar, obtener la nueva versión del paquete, ejecutar primero `--check`, revisar la lista y ejecutar `--apply` si no hay conflictos. Si los hay, conservar los originales y coordinar su conciliación; no borrar package.json, datos o secretos para eludirlos.

La instalación inicial DC-OPS-70 en el equipo original no tiene recibo de este instalador: si sus archivos difieren del payload, se consideran **no gestionados y conflictivos**. Este PR no autoriza sobrescribirlos automáticamente. La nueva instalación limpia y las actualizaciones gestionadas sí están probadas.

## Ejecución y datos

Consultar también `payload/ops/local/README.md`. Los comandos npm arrancan Angular/Nginx, Backend Java 21, PostgreSQL 16.15 y Mailpit local. No hay API Gateway formal, servicio Unreal ni activación del simulador académico por iniciar estos contenedores.

Por defecto el proyecto Docker es `prisaly-ops70-local`; web 18170, Mailpit 18025 y SMTP 11025, solo en loopback. En un ensayo separado se permite `PRISALY_OPS_PROJECT=prisaly-ops71-clean` y puertos propios en `ops/local/.env`; solo se admiten namespaces locales OPS70/OPS71, no nombres QA existentes. El proyecto elegido debe mantenerse igual para up/status/down. Esto se usa para la instalación limpia y no renombra ni reutiliza el proyecto original.

`down` no usa -v, prune o eliminación de imágenes. Conserva datos y auditoría. Guardar de forma privada `.secrets` junto con la copia de seguridad del volumen: perder la clave no autoriza al instalador/controlador a regenerarla para una base ya existente.

DD-02, M5 POST, fases, narrativa, evaluación M6 y workers permanecen desactivados. No se ejecuta inicialización administrativa. Flyway descubre únicamente las migraciones oficiales. Auth y FE-58 se conservan. Mailpit captura correo ficticio; no se habilita SMTP institucional.

## Pruebas

```powershell
node --test RUTA_PAQUETE\install.test.mjs
npm run check
```

Ocho pruebas del instalador cubren conflictos, idempotencia, actualizaciones, respaldos y protección de rutas. El smoke test de la raíz usa Docker propio y deja la instancia apagada, conservando su volumen. Comprueba salud, API protegida, Mailpit y persistencia tras down/up, y toma su propio inventario de contenedores al inicio: no necesita logs o archivos privados de una campaña anterior.

No se incluyen `.secrets`, `.env` local, `.build`, logs, bases, recibos, backups ni datos de pruebas. La evidencia pública del paquete se resume en `VALIDACION.md`; los artefactos privados permanecen fuera del PR.
