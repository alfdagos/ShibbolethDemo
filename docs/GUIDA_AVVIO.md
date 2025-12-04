# Guida all'Avvio del Progetto Shibboleth Demo

Questa guida descrive i passaggi necessari per avviare l'intero stack (Backend, Frontend, SP, IDP) su un cluster Kubernetes locale utilizzando **k3d**.

## Prerequisiti

Assicurati di avere installato:
- **Docker**
- **k3d**
- **kubectl**

## 1. Avvio del Cluster Kubernetes

Crea un cluster k3d mappando la porta 8080 locale al load balancer del cluster (porta 80).

```bash
sudo k3d cluster create k3s-default --port "8080:80@loadbalancer" --agents 2
```

## 2. Build delle Immagini Docker

Costruisci le immagini per i tre componenti principali. Esegui questi comandi dalla cartella `demo-app`:

```bash
# Backend
docker build -t demo-backend:latest ./backend

# Frontend
docker build -t demo-frontend:latest ./frontend

# Service Provider (SP)
docker build -t demo-sp:latest ./sp
```

## 3. Importazione delle Immagini nel Cluster

Importa le immagini appena create all'interno del cluster k3d in modo che Kubernetes possa utilizzarle.

```bash
sudo k3d image import demo-backend:latest demo-frontend:latest demo-sp:latest -c k3s-default
```

## 4. Deploy delle Risorse Kubernetes

Applica tutti i manifesti presenti nella cartella `k8s`. Questo creerà i Deployment, i Service, i ConfigMap e l'Ingress.

```bash
kubectl apply -f demo-app/k8s/
```

Attendi che tutti i pod siano in stato `Running`:

```bash
kubectl get pods -w
```

## 5. Configurazione DNS Locale

Per accedere ai servizi tramite i nomi a dominio configurati nell'Ingress, devi modificare il file `/etc/hosts` del tuo computer.

Aggiungi la seguente riga:

```text
127.0.0.1 sp.example.com idp.example.com
```

## 6. Test dell'Applicazione

1.  Apri il browser e vai su: **http://sp.example.com:8080/**
2.  Dovresti essere reindirizzato alla pagina di login dell'IDP (`idp.example.com`).
3.  Usa le seguenti credenziali:
    *   **Username**: `user1`
    *   **Password**: `user1pass`
4.  Dopo il login, verrai reindirizzato all'SP che mostrerà gli attributi ricevuti (incluso `cn`, `uid`, `mail`).
5.  Clicca su **Logout** per terminare la sessione e tornare alla pagina iniziale (che richiederà nuovamente il login).

## Note sulla Configurazione

*   **IDP Users**: Gli utenti sono definiti nel ConfigMap `idp-config` (definito in `idp-configmap.yaml`).
*   **SP Metadata**: L'SP carica i metadati staticamente dal ConfigMap `sp-config`.
*   **Attributi**: La mappatura degli attributi è configurata per accettare il formato "Basic" (`urn:oasis:names:tc:SAML:2.0:attrname-format:basic`).
