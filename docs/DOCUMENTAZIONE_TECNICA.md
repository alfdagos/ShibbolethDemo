# Documentazione Tecnica Completa - Shibboleth Demo Project

Questo documento fornisce una panoramica dettagliata dell'architettura, dei componenti e delle scelte configurative adottate nel progetto. L'obiettivo è spiegare non solo *come* è configurato il sistema, ma *perché* sono state fatte determinate scelte tecniche.

## 1. Panoramica Architetturale

Il progetto simula un ambiente di autenticazione federata **SAML 2.0** completo, eseguito interamente in locale su Kubernetes (k3d).

Il flusso logico è il seguente:
1.  L'utente accede all'applicazione (SP).
2.  L'SP intercetta la richiesta e redirige l'utente all'Identity Provider (IDP).
3.  L'utente si autentica sull'IDP.
4.  L'IDP genera una asserzione SAML contenente gli attributi dell'utente e lo rimanda all'SP.
5.  L'SP valida l'asserzione, estrae gli attributi e li passa all'applicazione Backend tramite header HTTP.

---

## 2. Componenti del Sistema

### 2.1. Service Provider (SP)
**Ruolo:** Proteggere l'applicazione e gestire il protocollo SAML.
**Tecnologia:** Apache HTTP Server + Modulo `mod_shib`.

#### Configurazioni Chiave e Motivazioni:

*   **Apache Reverse Proxy (`virtual-host-app.conf`):**
    *   *Configurazione:* `ProxyPass /api http://backend-service:8080/api`
    *   *Motivazione:* L'architettura Shibboleth classica prevede che il modulo `mod_shib` giri all'interno di Apache. Apache agisce come un "gatekeeper": autentica l'utente e poi inoltra la richiesta al backend Java, iniettando gli attributi utente come header HTTP sicuri (`Shib-Session-ID`, `cn`, `uid`, ecc.).

*   **Shibboleth Configuration (`shibboleth2.xml`):**
    *   **EntityID:** `https://sp.example.com/shibboleth`
        *   *Motivazione:* Identificativo univoco dell'SP nella federazione.
    *   **SSO EntityID:** `https://idp.example.com/simplesaml/saml2/idp/metadata.php`
        *   *Motivazione:* Indica all'SP a quale IDP inviare l'utente per il login.
    *   **MetadataProvider (Statico):**
        *   *Configurazione:* Tipo `LocalDynamic` puntato a `/etc/shibboleth/idp-metadata.xml` (montato via ConfigMap).
        *   *Motivazione:* Inizialmente l'SP cercava di scaricare i metadati via HTTP all'avvio. In un ambiente Kubernetes dinamico, se l'IDP non è ancora pronto, l'SP va in crash (`No MetadataProvider available`). Usare un file locale montato via ConfigMap garantisce che l'SP parta sempre, disaccoppiando l'avvio dei due servizi.

*   **Attribute Map (`attribute-map.xml`):**
    *   *Modifica:* Aggiunta del supporto per `urn:oasis:names:tc:SAML:2.0:attrname-format:basic`.
    *   *Motivazione:* SimpleSAMLphp (l'IDP usato) invia gli attributi nel formato "Basic" (solo nome, es. `cn`). Shibboleth di default si aspetta il formato "URI" (es. `urn:oid:2.5.4.3`). Senza questa mappatura esplicita, l'SP riceveva l'asserzione valida ma scartava tutti gli attributi, risultando in header vuoti nel backend.

### 2.2. Identity Provider (IDP)
**Ruolo:** Autenticare l'utente e fornire le informazioni (attributi).
**Tecnologia:** SimpleSAMLphp (immagine `kenchan0130/simplesamlphp`).

#### Configurazioni Chiave e Motivazioni:

*   **Configurazione Utenti (`authsources.php` in `idp-configmap.yaml`):**
    *   *Configurazione:* Definizione statica dell'array `$config` con utenti `user1` e `user2`.
    *   *Motivazione:* L'immagine Docker base ha una configurazione generica. Abbiamo sovrascritto questo file tramite Kubernetes ConfigMap per:
        1.  Definire password note per il test.
        2.  Assicurarci che vengano restituiti attributi specifici (`cn`, `uid`, `mail`) necessari al backend per dimostrare il funzionamento.
    *   *Nota Tecnica:* Il file è stato inserito direttamente nel ConfigMap per evitare di dover ricostruire l'immagine Docker dell'IDP ad ogni modifica degli utenti di test.

### 2.3. Backend
**Ruolo:** Logica di business e visualizzazione dati.
**Tecnologia:** Java Spring Boot.

#### Configurazioni Chiave e Motivazioni:

*   **Controller (`UserController.java`):**
    *   *Logica:* Legge gli header della richiesta HTTP (es. `request.getHeader("cn")`).
    *   *Motivazione:* Il backend non implementa SAML. Si "fida" ciecamente degli header ricevuti perché, nell'architettura Kubernetes, solo il pod dell'SP può contattare il backend (grazie alla configurazione di rete interna, anche se qui semplificata). Questo pattern è chiamato "Termination at the Proxy".

### 2.4. Frontend
**Ruolo:** Interfaccia utente semplice.
**Tecnologia:** Nginx + HTML statico.

*   *Motivazione:* Serve solo come punto di ingresso visivo per verificare che i container siano attivi, anche se il test principale avviene chiamando l'SP.

---

## 3. Infrastruttura Kubernetes

### 3.1. Ingress (`ingress.yaml`)
**Ruolo:** Gestione del traffico in ingresso e routing basato su domini.

*   **Host:** `sp.example.com` e `idp.example.com`.
*   **Motivazione:** Il protocollo SAML è sensibile ai domini e ai cookie. Usare `localhost` con porte diverse (es. `localhost:8081`, `localhost:8082`) spesso causa problemi di sicurezza nei browser (SameSite cookies) o configurazioni errate nei metadati SAML. Simulare domini reali tramite `/etc/hosts` e Ingress crea un ambiente di test fedele alla produzione.

### 3.2. ConfigMaps (`sp-configmap.yaml`, `idp-configmap.yaml`)
**Ruolo:** Iniezione delle configurazioni a runtime.

*   **Motivazione:**
    *   Permette di modificare la configurazione SAML (es. aggiungere un attributo, cambiare un EntityID) modificando solo lo YAML e riavviando i pod, senza dover ricompilare le immagini Docker.
    *   Risolve il problema della persistenza dei metadati e della configurazione PHP personalizzata.

---

## 4. Flusso dei Dati (Data Flow)

1.  **Browser** -> `http://sp.example.com:8080/`
2.  **Ingress** -> Pod **SP** (Apache)
3.  **SP** nota che non c'è sessione -> Redirige a `idp.example.com`.
4.  **Browser** -> `http://idp.example.com:8080/`
5.  **Ingress** -> Pod **IDP** (SimpleSAMLphp)
6.  **IDP** mostra form di login -> Utente inserisce credenziali.
7.  **IDP** valida e genera XML (SAML Response) -> Redirige (POST) verso `sp.example.com/Shibboleth.sso/SAML2/POST`.
8.  **SP** riceve XML, verifica firma, decripta (se necessario) e mappa gli attributi (grazie a `attribute-map.xml`).
9.  **SP** fa proxy della richiesta verso **Backend** aggiungendo header:
    *   `cn: User One`
    *   `uid: 1`
10. **Backend** risponde con JSON contenente i dati ricevuti.
11. **SP** restituisce il JSON al **Browser**.
