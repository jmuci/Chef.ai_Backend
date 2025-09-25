# chefai

[![Unit Tests Status](https://github.com/jmuci/Chef.ai_Backend/actions/workflows/unit-tests-workflow.yml/badge.svg)](https://github.com/jmuci/Chef.ai_Backend/actions/workflows/unit-tests-workflow.yml)

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                                      |
|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                                |
| [Static Content](https://start.ktor.io/p/static-content)               | Serves static files from defined locations                                                       |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers               |
| [Exposed](https://www.jetbrains.com/exposed/)                          | Kotlin SQL library and tools: DSL, DAO Framework, ORM`                                           |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                                   |
| [Thymeleaf](https://ktor.io/docs/server-thymeleaf.html)                | Thymeleaf is a modern server-side Java template engine for both web and standalone environments. 

## Verifications 

Unit tests run on PRs in Github

### End to End Smoke Test

```bash
./smoke-test.sh
```

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                        | Description                                                                |
|---------------------------------------------|----------------------------------------------------------------------------|
| `./gradlew test`                            | Run the tests                                                              |
| `./gradlew build`                           | Build everything                                                           |
| `buildFatJar`                               | Build an executable JAR of the server with all dependencies included       |
| `buildImage`                                | Build the docker image to use with the fat JAR                             |
| `publishImageToLocalRegistry`               | Publish the docker image locally                                           |
| `run`                                       | Run the server                                                             |
| `runDocker`                                 | Run using the local docker image                                           |
| ` docker compose -f docker-compose.yaml up` | Run with docker compose which will also start and connect to a Postgres Db |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

**NOTE**: The service requires the DB to be running (see [next section](#docker-compose))

### Docker Compose
To start only the DB (as you might want to start the service on the IDE for debugging):
```bash
docker compose up db
```
Add -d for detached mode. 

```bash
docker compose down db
```
To start both the DB and the service, run: 
```bash
 docker compose -f docker-compose.yaml up --build
```

## Postgres CheatSheet
# PostgreSQL psql Command Cheat Sheet

A quick reference of the most useful psql commands while developing with Postgres.

---

## 🔐 Connection & Exit

| Action | Command |
|--------|---------|
| Connect to a DB | `psql -U <user> -d <db>` |
| Connect to another DB (inside psql) | `\c <db>` |
| Quit psql | `\q` |
| Show connection info | `\conninfo` |

---

## 📚 List Databases, Tables, Schemas, Users

| Action | Command |
|--------|---------|
| List all databases | `\l` |
| List all tables in current schema | `\dt` |
| List all tables + system tables | `\dt+` |
| List schemas | `\dn` |
| List users / roles | `\du` |
| List indexes | `\di` |
| List functions | `\df` |

---

## 🧭 Inspecting Structures

| Action | Command |
|--------|---------|
| Describe table structure | `\d <table>` |
| Describe with details | `\d+ <table>` |
| Show all relations | `\d` |
| Show search path | `SHOW search_path;` |

---

## 📊 Query Display Options

| Action | Command |
|--------|---------|
| Toggle expanded mode | `\x` |
| Auto-expanded mode | `\x auto` |
| Show command history | `\s` |

---

## 🛠 Admin & Utility Commands

| Action | Command |
|--------|---------|
| Create database | `CREATE DATABASE name;` |
| Drop database | `DROP DATABASE name;` |
| Create user | `CREATE USER name WITH PASSWORD 'pass';` |
| Grant privileges | `GRANT ALL PRIVILEGES ON DATABASE db TO user;` |

---

## 📁 Import / Export Data

| Action | Command |
|--------|---------|
| Run SQL file | `psql -U user -d db -f file.sql` |
| Export table → CSV | `\copy table TO '/path/out.csv' CSV HEADER;` |
| Import CSV → table | `\copy table FROM '/path/in.csv' CSV HEADER;` |

---

## 🆘 Help & Reference

| Action | Command |
|--------|---------|
| List all psql meta-commands | `\?` |
| SQL command help | `\h` |
| Help for specific SQL command | `\h CREATE TABLE` |

---

