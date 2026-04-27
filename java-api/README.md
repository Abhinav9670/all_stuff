# Microservices with Spring Boot and Spring Cloud on Kubernetes Demo Project
In this project I'm demonstrating you the most interesting features of [Spring Cloud Project](https://spring.io/projects/spring-cloud) for building microservice-based architecture that is deployed on Kubernetes. All the samples may be easily deployed on local Kubernetes single-node cluster - Minikube.


## Getting Started
Currently you may find here some examples of microservices implementation using different projects from Spring Cloud. All the examples are divided into the branches and described in a separated articles on my blog. Here's a full list of available examples:

. An introduction to Spring Cloud Kubernetes project, that shows its the most interesting features like discovery across many namespaces or Spring Boot property sources based on ConfigMap and Secret.

### Usage
1. Download and run **Minikube** using command: `minikube start --vm-driver=virtualbox --memory='4000mb'`
2. Build Maven project with using command: `mvn clean install`
3. Build Docker images for each module using command, for example: `docker build -t quote:.`
4. Go to `/kubernetes` directory in repository
5. Apply all templates to Minikube using command: `kubectl apply -f <filename>.yaml`
6. Check status with `kubectl get pods`

## Architecture

Our sample microservices-based system consists of the following modules:
- **gateway-service** - a module that Spring Cloud Netflix Zuul for running Spring Boot application that acts as a proxy/gateway in our architecture.
- **quote-service** - a module containing the first of our sample microservices that allows to perform CRUD operation on Mongo repository of quote. It communicates with order-service.
- **order-service** - a module containing the second of our sample microservices that allows to perform CRUD operation on Mongo repository of orders.
- **utility-service** - a module containing the third of our sample microservices that allows to perform CRUD operation on Mongo repository of utility. It communicates with order-service .
- **admin-service** - a module containing embedded Spring Boot Admin Server used for monitoring Spring Boot microservices running on Kubernetes
The following picture illustrates the architecture described above including Kubernetes objects.

<img src="#" title="Architecture1">

You can distribute applications across multiple namespaces and use Spring Cloud Kubernetes `DiscoveryClient` and `Ribbon` for inter-service communication.


**After cloning the repository, run the following command to configure Git hooks for checkstyle & PMD checks:**
./setup_hooks.sh
