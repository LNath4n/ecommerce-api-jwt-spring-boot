# E-commerce API: Spring Boot + JWT

REST API for an e-commerce platform, built with Spring Boot. It covers the product catalog, shopping cart, PayPal checkout and secure authentication, and it is deployed on AWS with a CI/CD pipeline.

**Live demo:** https://d23ev6ydugwou7.cloudfront.net/productos
**Frontend repo:** [ecommerce-web-jwt-angular](https://github.com/LNath4n/ecommerce-web-jwt-angular)

---

## Key Features

- **JWT authentication**: stateless login and session handling with Spring Security.
- **Dynamic filtering with JPA Specifications**: product search by multiple combinable criteria.
- **Shopping cart and PayPal checkout**: full purchase flow.
- **Rate limiting with Bucket4j + Redis**: per-client limits to protect the API from abuse.
- **Query optimization**: N+1 problems fixed with fetch joins / `@EntityGraph` on endpoints with nested relationships.
- **API documentation**: endpoints documented with Swagger/OpenAPI.
- **Testing**: unit tests with JUnit and Mockito, plus a Postman suite of 2,600+ tests.

---

## Tech Stack

| Area | Technologies |
|------|--------------|
| Backend | Java, Spring Boot, Spring Security (JWT), Spring Data JPA / Hibernate |
| Data | PostgreSQL, Redis |
| Testing | JUnit, Mockito, Postman |
| DevOps | Docker, GitHub Actions (CI/CD) |
| Cloud (AWS) | ECR, EC2, RDS (PostgreSQL), ElastiCache (Redis), IAM, CloudFront |

---

## Architecture

Layered Spring Boot architecture:

```
Controller → Service → Repository → Entity
```

- **Controllers** expose the REST API and are documented with Swagger.
- **Services** contain the business logic.
- **Repositories** use Spring Data JPA, with Specifications for dynamic queries.
- **Entities** are tuned to avoid N+1 issues.

### Deployment

On every push, GitHub Actions runs the tests, builds the Docker image, pushes it to Amazon ECR and deploys it to EC2. The database runs on RDS (PostgreSQL) and Redis on ElastiCache, and EC2 accesses AWS resources through an IAM role.

---

## Local Setup

### Prerequisites

- Java (see `pom.xml` for the exact version)
- Maven (or the included `./mvnw`)
- PostgreSQL and Redis, running locally or via Docker

### 1. Clone the repository

```bash
git clone https://github.com/LNath4n/ecommerce-api-jwt-spring-boot.git
cd ecommerce-api-jwt-spring-boot
```

### 2. Configure the database

```sql
CREATE DATABASE ferreviky;
```

Set your credentials in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ferreviky
spring.datasource.username=your_username
spring.datasource.password=your_password

jwt.secret=your_secret_key
```

Redis (rate limiting) and PayPal sandbox credentials are also configured there; check `application.properties` for the exact property names.

### 3. Run the project

```bash
./mvnw clean install
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 4. API documentation

With the project running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

---

## Author

**Nathan Vargas**: Backend-focused Full Stack Developer (Java · Spring Boot · AWS)
[GitHub](https://github.com/LNath4n) · [LinkedIn](https://www.linkedin.com/in/nathanvargas)
