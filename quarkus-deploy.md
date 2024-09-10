# Deploying the application on Kubernetes

## Minikube instructions

First, make sure that you have Docker and minikube installed.

Next, point your shell to minikube's docker daemon:

```bash
minikube start --driver=docker
```

Then, install postgres into the cluster with the following commands in order:

```bash
kubectl apply -f deploy/kubernetes/persistent-volume.yaml
kubectl apply -f deploy/kubernetes/persistent-volume-claim.yaml
kubectl apply -f deploy/kubernetes/postgresql-deployment.yaml
kubectl apply -f deploy/kubernetes/postgresql-service.yaml
```

Next, build the project:

```bash
mvn clean compile package -Dquarkus.kubernetes.deploy=true
```

Then, get the URL of the project via:

```bash
minikube service list
```

## Openshift instructions

To deploy to Openshift via the Openshift extension

```bash 
oc new-app -e POSTGRESQL_USER=quarkus \                                                                                                             
            -e POSTGRESQL_PASSWORD=quarkus \
            -e POSTGRESQL_DATABASE=coolstore \
            openshift/postgresql:latest \
            --name=postgres
``` 

Make sure that you are logged in via terminal to OpenShift via the `oc` command

```mvn
mvn clean compile package -Dquarkus.kubernetes.deploy=true
```

