# 🏥 Patient Management Microservices System

This is a **Dockerized microservices-based** patient management system designed to demonstrate service orchestration, inter-service communication, authentication, and analytics in a healthcare-style architecture.

---

## 🚀 Features

- **Microservices Architecture**
  - `auth-service`: Handles user authentication and login security
  - `patient-service`: Manages patient data and profiles
  - `billing-service`: Handles billing and payment processing
  - `analytics-service`: Consumes Kafka events for analytics and reporting
  - `api-gateway`: Entry point for routing requests to backend services
  - `api-requests`: Manages request aggregation and forwarding

- **Event Streaming** with **Apache Kafka**
  - Services communicate via events published to Kafka topics

- **Data Persistence** with **PostgreSQL**
  - Each service connects to its own database container via an internal Docker network

- **Authentication & Security**
  - Authenticated endpoints secured via token-based login

- **Docker & Docker Compose**
  - Entire system is orchestrated using Docker Compose
  - All services run in isolated containers and share an internal Docker network

---

## 🧱 Tech Stack

| Component         | Technology         |
|------------------|--------------------|
| API Gateway      | Spring Boot / Java |
| Microservices     | Spring Boot / Java |
| Event Bus        | Apache Kafka       |
| Databases        | PostgreSQL         |
| Containerization | Docker             |
| Orchestration    | Docker Compose     |

---

## 🧪 Getting Started

### Prerequisites
- Docker
- Docker Compose

### To Run the System

```bash
docker-compose up --build
