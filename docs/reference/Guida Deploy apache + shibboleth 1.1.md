# Guida Deploy Apache + Shibboleth su OpenShift

**Versione:** 1.0  
**Autore:** Example Vendor  
**Cliente:** Example Corp - Example Organization

-----

## 1\. Introduzione

Il seguente documento fornisce le linee guida per integrare un applicativo, eseguito su piattaforma OpenShift, che necessita di integrazione con Identity Provider basato su Shibboleth.

Gli step previsti sono i seguenti:

  * Preparazione della build immagine applicativa, predisponendo le librerie a supporto.
  * Configurazione del Service Provider (SP).
  * Rilascio applicativo su Openshift per mezzo di Pipeline.
  * Predisposizione delle risorse Openshift:
      * Service
      * Rotte
      * Secrets
      * ConfigMaps

> **N.B.** Il contenuto delle configurazioni da rilasciare su Openshift sarà soggetto ai requisiti applicativi.

### 1.1 Riferimenti

| RIF | Titolo | Url/Autore |
| :--- | :--- | :--- |
| 1 | Using ConfigMaps with application | [Red Hat Documentation](https://www.google.com/search?q=https://docs.redhat.com/en/documentation/openshift_container_platform/4.16/html/building_applications/config-maps%23nodes-pods-configmap-overview_config-maps) |
| 2 | Rilascio\_applicativo\_su\_piattaforme\_Openshift\_v1.1.docx | Documentazione interna |

Per eventuali dubbi o chiarimenti si faccia riferimento a: `middleware.cloud@example.com`

-----

## 2\. Flusso CI/CD

Seguendo la modalità standard di sviluppo adottata da Example Corp, che prevede l'integrazione con il sistema ALM, in OpenShift è disponibile un flusso CI/CD dedicato alla build e al rilascio automatico delle applicazioni a microservizi basate su container.

Questi container sono gestiti tramite oggetti logici di tipo **deployment**, generati dall'apposita pipeline predisposta a tale scopo. Tutti i rilasci e le modalità di configurazione applicative mostrate nel documento non impattano tali modalità.

> *[Riferimento Immagine: Figura 1 - Flusso CI/CD]*

Per maggiori informazioni riguardo il flusso CI/CD Standard utilizzato da Example Corp, si rimanda al documento censito nella tabella dedicata ai riferimenti (RIF-2).

-----

## 3\. Predisposizione Custom Image per Openshift

Per garantire la copertura del supporto Red Hat sulle immagini del sistema operativo utilizzate dalle applicazioni, sarà necessario adottare esclusivamente le build fornite dal catalogo ufficiale del vendor.

### 3.1 Esempio di una Custom Image con Web Server Apache HTTPD

Di seguito viene riportato un esempio di configurazione che integra il web server Apache con il modulo Shibboleth. Oltre alle configurazioni, saranno illustrati anche i moduli necessari da predisporre all'interno del sistema.

#### 3.1.1 Obiettivo

L'obiettivo è creare un'immagine custom partendo da quella base per un container che integri:

  * Apache HTTPD;
  * Modulo `mod_shib` del Shibboleth Service Provider;
  * Configurazioni per l'integrazione con l'Identity Provider (IdP);
  * Deployment scalabile su OpenShift.

#### 3.1.2 Prerequisiti

1.  **Kick-off con team Security:** Individuazione delle configurazioni relative all'IDP di riferimento secondo le esigenze applicative (utenti: esterni, interni, spid/CIE).
2.  **Accesso al cluster OpenShift:** Possibilità di creare progetti, deployment, configmap, secrets nel proprio namespace.
3.  **Repository Git su ALM:** Abilitato per depositare i sorgenti per la pipeline CI.
4.  **Metadata Shibboleth dell'IdP (XML):** Es. `idp-metadata.xml`.
5.  **Certificati SP:** Necessari per proteggere le informazioni tra IdP e SP.
      * `sp-key.pem`
      * `sp-cert.pem`
      * *(Possono essere generati via `shib-keygen` o manualmente con `openssl`)*.

#### 3.1.3 Dockerfile/Containerfile

Esempio di `Dockerfile` da collocare nella struttura ALM. Include i riferimenti alle configurazioni base per Shibboleth.

```dockerfile
FROM registry.redhat.io/ubi9/httpd-24
USER 0

EXPOSE 8080
EXPOSE 8443

## Applicativo web
ADD index.php /tmp/src/index.php
RUN chown -R 1001:0 /tmp/src

# Virtual host applicativo viene letto da apache
ADD virtual-host-app.conf /opt/app-root/etc/httpd.d/virtual-host-app.conf
RUN chown -R 1001:0 /opt/app-root/etc/httpd.d/

# Repository shibboleth service provider per rhel9
COPY "shibboleth.repo" "/etc/yum.repos.d/shibboleth.repo"

# Installazione aggiornamenti e pacchetti software
RUN dnf -y update httpd openssl && \
    dnf -y install shibboleth && \
    dnf clean all

## Configurazione di shibboleth
## Certificati SP Shibboleth (non sono i certificati SSL)
ADD pem/sp-signing-key.pem /etc/shibboleth/sp-signing-key.pem
ADD pem/sp-signing-cert.pem /etc/shibboleth/sp-signing-cert.pem
ADD pem/sp-encrypt-cert.pem /etc/shibboleth/sp-encrypt-cert.pem
ADD pem/sp-encrypt-key.pem /etc/shibboleth/sp-encrypt-key.pem

# Per OpenShift bisogna dare i seguenti permessi ai pem
RUN chmod 640 /etc/shibboleth/*.pem

## File metadata idp, attributi e configurazione
ADD identity-metadata.xml /etc/shibboleth/identity-metadata.xml
ADD attribute-map.xml /etc/shibboleth/attribute-map.xml
ADD shibboleth2.xml /etc/shibboleth/shibboleth2.xml
RUN chown -R 1001:0 /etc/shibboleth/

## Permessi per la creazione del pid per l'avvio del servizio
RUN chown -R 1001:0 /etc/shibboleth/
RUN mkdir -p /var/run/shibboleth
RUN chown -R 1001:0 /var/run/shibboleth
RUN chmod 775 /var/run/shibboleth
RUN chown -R 1001:0 /var/log/shibboleth/
RUN chmod 775 /var/log/shibboleth/

## Configurazione del file run-httpd con l'avvio del modulo shibboleth sp
ADD run-httpd /usr/bin/run-httpd
RUN chown 1001:0 /usr/bin/run-httpd
RUN chmod +x /usr/bin/run-httpd

USER 1001

# Let the assemble script install the dependencies
RUN /usr/libexec/s2i/assemble

# The run script uses standard ways to run the application
CMD /usr/libexec/s2i/run
```

**Nota sul file `run-httpd`:**
Il file deve essere configurato per l'avvio concorrente di `httpd` e `shibd`. Aggiungere o modificare la riga finale:

```bash
exec httpd -D FOREGROUND | exec shibd -f -F $@
```

> **N.B.** OpenShift non permette l'esecuzione come root. Assicurarsi che:
>
>   * I file siano group-writable.
>   * Il container funzioni con UID arbitrario.

#### 3.1.4 Configurazione Apache per Shibboleth

Esempio minimale di `httpd.conf`:

```apache
<VirtualHost *:8080>
    ServerName myservice.example.com
    DocumentRoot /var/www/html

    <Location />
        AuthType shibboleth
        ShibRequestSetting applicationId (configurazione con più url)
        ShibRequestSetting requireSession true
        Require shib-session
        ShibUseHeaders On
    </Location>

    ErrorLog /proc/self/fd/2
    CustomLog /proc/self/fd/1 combined
</VirtualHost>
```

> **N.B.** Nell'uso di container, i campi `ErrorLog` e `CustomLog` devono reindirizzare su Stderr e Stdout, non su file.

#### 3.1.5 Configurazione di Shibboleth SP

Esempio minimale di `shibboleth2.xml`:

```xml
<Site id="1" name="myapp1.example.com"/>
<RequestMap>
    <Host name="myapp1.example.com">
        <Path name="secure" authType="shibboleth" requireSession="true"/>
    </Host>
    <Host name="myapp2.example.com" applicationId="app-spid-asp" authType="shibboleth" requireSession="true"/>
</RequestMap>

<ApplicationDefaults entityID="https://myapp1.example.com/">
    <SessionInitiator type="Chaining" Location="/Login" isDefault="true" id="Login" entityID="https://idp.example.com/idp/shibboleth">
    
    <ApplicationOverride id="myapp1" entityID="https://myapp1.example.com/"/>
    <ApplicationOverride id="myapp2" entityID="https://myapp2.example.com/"/>
</ApplicationDefaults>
```

> **N.B.** Questa configurazione verrà gestita preferibilmente tramite **ConfigMap** (vedi capitolo 4.3) per evitare configurazioni scolpite nell'immagine.

### 3.2 Custom Image Nginx (WIP)

*(Attualmente in fase di predisposizione delle configurazioni)*

-----

## 4\. Configurazioni Delle Risorse Openshift

In questo paragrafo vengono illustrate le operazioni per gestire le configurazioni da integrare, inclusa l'amministrazione dei certificati e la gestione delle rotte.

> *[Riferimento Immagine: Figura 2 - Networking Applicativo]*
> Flusso: External Route -\> Service -\> Pod (che monta ConfigMap e Secret)

Le risorse principali da configurare sono:

  * **Service**
  * **Route** (interne ed esterne)
  * **Secret** (per certificati)
  * **ConfigMaps** (per configurazioni applicative)

### 4.1 Networking Applicativo - Predisposizione Route Applicative

È necessario configurare:

1.  **Service:** Punto di accesso stabile ai Pod.
2.  **Route:** Esposizione del servizio verso l'esterno.

Per le rotte sono richieste due configurazioni distinte per tutti gli ambienti (Sviluppo, Collaudo, Produzione):

  * **Rotta Esterna:** Accesso utente finale.
  * **Rotta Interna:** Accesso tecnico per gruppi di lavoro.

**Nota per l'ambiente di Produzione (Siti Balbo e Inail):**

  * **Sito Balbo (Principale):** Route esterna `www.example.com`, Route interna `https://backoffice-nsi.apps.pr1epaas.cloud.example.com`
  * **Sito Inail (Secondario):** Route esterna `www.example.com`, Route interna `https://backoffice-nsi.apps.pr2epaas.cloud.example.com`

#### 4.1.1 Configurazione Service

OpenShift si connette internamente via HTTP, ma per sicurezza il traffico interno sarà protetto da certificati gestiti dalla piattaforma.
È possibile generare un certificato *self-signed* tramite annotation nel Service. Questo creerà automaticamente un Secret.

```yaml
metadata:
  annotations:
    service.beta.openshift.io/serving-cert-secret-name: myapp-service-tls
```

#### 4.1.2 Configurazione Route Interna

La route interna deve usare la modalità **Re-encrypt**. Il traffico viene terminato dal router, decifrato e recifrato verso il servizio interno.

> *[Riferimento Immagine: Figura 4 - Rotta Interna]*

> **N.B.** Non è necessario caricare un certificato nella Route; verrà usato quello della piattaforma.

#### 4.1.3 Configurazione Route Esterna

Per la Route destinata all'utente finale, configurare **Secure Route** con **TLS termination**.

> *[Riferimento Immagine: Figura 5 - Rotta Esterna]*

### 4.2 Configurazione Secrets per Certificati

I Secrets gestiscono credenziali o certificati dinamicamente.

> *[Riferimento Immagine: Figura 6 - Secret per Certificato]*

> **N.B.** I file contenenti chiavi e certificati devono seguire la naming convention: `tls.crt` e `tls.key`.

#### 4.2.1 Mount Secrets

I Secrets possono essere montati come volumi nel container. Utilizzare la funzione "Add Secret to workload" dalla console OpenShift.

> *[Riferimento Immagine: Figura 7 e 8 - Mount Secret/Path]*

### 4.3 Configurazione ConfigMaps

Le ConfigMap permettono di iniettare file e variabili, svincolando la configurazione dalla build.

#### 4.3.1 Mount ConfigMaps

Esistono due modalità di iniezione nel Deployment:

1.  **Come variabili d'ambiente (`envFrom`):**

    ```yaml
    envFrom:
      - configMapRef:
          name: nome-configmap
    ```

2.  **Come volume montato (`volumes` e `volumeMounts`):**

    ```yaml
    volumes:
      - name: config-volume
        configMap:
          name: nome-configmap
    containers:
      - name: nome-container
        volumeMounts:
          - name: config-volume
            mountPath: /path/in/container
    ```

#### 4.3.2 Configurazione ConfigMap Web Server Apache

Si consiglia un approccio modulare con 2 ConfigMaps:

1.  **Configurazione Apache:** Parametrizzata con variabili.
2.  **Variabili d'ambiente:** Specifiche per il cluster target.

**Esempio ConfigMap Apache (httpd.conf parametrizzato):**

```apache
## Virtual Hosts
<VirtualHost *:8080>
    ServerName ${APPNAME}.${CLUSTERDOMAIN}
    ServerAlias ${APPNAME}.apps
    ServerAdmin ${SERVERADMIN}

    RewriteEngine On

    <Location "/server-status">
        SetHandler server-status
    </Location>

    <Directory "/opt/app-root/src/">
        Options FollowSymLinks
        DirectoryIndex index.php index.html
        AllowOverride All
        Require all granted
    </Directory>

    # Attivazione lato SP del S.S.O.
    <Location />
        AuthType shibboleth
        ShibRequestSetting applicationId ${APPNAME}-cloud
        ShibRequestSetting requireSession true
        Require shib-session
        ShibUseHeaders On
    </Location>

    LogFormat "%{X-ClientIP}i %l %u %t \"%r\" %>s %b \"%{Referer}i\" \"%{User-Agent}i\" %v P=%p BE=%A ResTime=%D" combined
    ErrorLog /proc/self/fd/2
    CustomLog /proc/self/fd/1 combined
</VirtualHost>

# VirtualHost SSL (Simile al precedente, su porta 8443)
<VirtualHost *:8443>
    # ... configurazione analoga con Include vhconf/vh-ssl_wildcloud.cnf ...
</VirtualHost>
```

**Esempio ConfigMap Variabili:**

```yaml
data:
  APPNAME: your-app-name
  CLUSTERDOMAIN: your-cluster-domain
  SERVERADMIN: server.admin@example.com
```

> **N.B.** Le configurazioni sopra sono esempi e devono essere adattate al contesto applicativo.

#### 4.3.3 Configurazione ConfigMap Web Server Ngnix (WIP)

*(Attualmente in fase di predisposizione delle configurazioni)*