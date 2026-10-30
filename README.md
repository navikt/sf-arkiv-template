## Fra template til teaminstans

Dette template-repoet er utgangspunktet for en egen `sf-arkiv`-instans i teamets namespace.

1. Velg «Use this template» → «Create a new repository» i GitHub, og gi repoet et navn, for eksempel `sf-arkiv-<team-namespace>`. Legg det nye repoet til under Repositories for teamet i Nais Console, slik at GitHub Actions får deploye til teamets namespace.
2. Bruk IDE-ens søk og erstatt i hele prosjektet: erstatt `template` med teamets namespace. Dette oppdaterer blant annet namespace, team, ingresser, workflow og lenker. Kontroller at `team` i workflow og Nais-manifestene er teamets faktiske Nais-team hvis det er forskjellig fra namespace. Behold appnavnet og ConfigMap-navnet `sf-arkiv` og Salesforce-reglene med `teamcrm` og `dev-external`/`prod-external`.
3. Kontroller at `ArkivTable` samsvarer med ønsket datamodell. Ved endringer må du også oppdatere DTO-en `Arkiv`, tilhørende funksjoner, tester og OpenAPI-spesifikasjonen. Kontroller også `gcp.sqlInstances` i Nais-manifestene: dev bruker minste tier (`db-f1-micro`), mens prod bruker minste tier som omfattes av Cloud SQL-SLA (`db-custom-1-3840`), med `diskAutoresize: true`.
4. Gå til teamets namespace i Nais Console og legg til det nye repoet under Repositories for å tillate deploy. Opprett en ConfigMap med navnet `sf-arkiv` i teamets namespace i både `dev-gcp` og `prod-gcp`. Legg inn verdiene i tabellen nedenfor som strenger, uten anførselstegn i selve verdien.

   Et raskt alternativ er å bruke fila `configmap` i repoets rot. Kjør fra repoets rot:

   ```sh
   kubectl apply -f configmap -n <team-namespace> --context dev-gcp
   kubectl apply -f configmap -n <team-namespace> --context prod-gcp
   ```

| ConfigMap-nøkkel | Før første deploy (dev og prod) | Etter initialisering (dev og prod) |
|---|---------------------------------|---|
| `DEV_TEST_BYPASS_ALLOWED` | `false`                         | `false` |
| `HENTE_ALLOWED` | `false`                         | `false` |
| `DB_INIT_ALLOWED` | `true`                          | `false` |
| `DUMMY_READ_TO_TEST_READINESS` | `false`                         | `true` |

5. Deploy til dev via GitHub Actions. Workflowen deployer til dev ved push og til prod ved push til `main`. Klargjør derfor begge miljøene før første push til `main`. Nais oppretter Cloud SQL-databasen og Entra-appen fra manifestet. Nais injiserer databasetilgang og Entra-konfigurasjon, så dette skal ikke legges i ConfigMap. Første deploy kan ta tid og gi en timeout-feil mens Nais oppretter databasen.
6. Initialiser tabellen i hvert miljø før appen tas i bruk. Se til att `DB_INIT_ALLOWED=true`, start podden på nytt og åpne `/internal/initDb` via intern ingress. Endpointet oppretter tabellen i databasen. Sett deretter `DB_INIT_ALLOWED=false` og `DUMMY_READ_TO_TEST_READINESS=true`, og start podden på nytt. La sistnevnte være `false` frem til tabellen finnes, ellers feiler appen ved oppstart. Kontroller `/internal/activeConfig` og at podden blir ready. Endringer i ConfigMap som brukes som miljøvariabler trigger omstart.
7. Konfigurer Salesforce til å bruke den nye instansens eksterne ingress og hente M2M-token med scope `api://dev-gcp.<team-namespace>.sf-arkiv/.default` i dev og `api://prod-gcp.<team-namespace>.sf-arkiv/.default` i prod. Inbound-reglene gir de oppførte Salesforce-klientene Entra-tilgang. Ved behov for testing i dev kan `HENTE_ALLOWED` og `DEV_TEST_BYPASS_ALLOWED` settes til `true` midlertidig. Bruk bare testdata, og sett dem tilbake til `false` etterpå.

Interne lenker krever tilgang til Navs interne nettverk, for eksempel via naisdevice. Ikke legg hemmeligheter i ConfigMap eller i Git.

Fjern denne seksjonen når teaminstansen er satt opp.

---

# sf-arkiv-template [<img align="right" src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png" width="18" alt="GitHub repository" />](https://github.com/navikt/sf-arkiv-template)
Lokalarkivinstans for template (testoppsett)

Appen tilbyr et API for lagring og henting av dokumentasjon fra Salesforce i PostgreSQL (Cloud SQL) på NAIS GCP.
Denne instansen er et testoppsett i templates namespace. Det skal opprettes egne varianter i tre andre namespace, eid av de respektive teamene.

Se swagger for API:

[![Swagger UI (dev)](https://img.shields.io/badge/DEV-Swagger_UI-2ea043)](https://sf-arkiv-template.intern.dev.nav.no/internal/swagger-ui/)
[![Swagger UI (prod)](https://img.shields.io/badge/PROD-Swagger_UI-blue)](https://sf-arkiv-template.intern.nav.no/internal/swagger-ui/)

## Lagring og henting

**`POST /arkiv`** lagrer en ikke-tom JSON-liste i én transaksjon. Bare `dokumentdato` er påkrevd (`yyyy-MM-dd`); øvrige felt er valgfrie.
Se Swagger for modellene. Kvitteringen inneholder ikke dokumentinnhold eller identifikasjonsfeltene `aktoerid`, `fnr` og `orgnr`.

**`POST /hente`** er ment for å verifisere integrasjonen og bør normalt slås av etter testing.
Send bare feltene du vil filtrere på, for eksempel `{"kilde":"test"}`. Minst ett felt må være utfylt;
alle utfylte felt må matche (AND). Responsen inneholder dokumentinnholdet.

**`konfidentiellt: true`** gir et ekstra vern: dataene lagres, men kan ikke hentes tilbake via `/hente`, selv når henting er aktivert.
Flagget er valgfritt, standardverdien er `false`, og verdien må være en boolsk JSON-verdi.
Responsheaderen `x-konfidentiellt-skipped` fra `/hente` angir antall utelatte treff og røper dermed at de finnes.

**`GET /authping`** kan brukes til å kontrollere om et kall har et gyldig token. `/authping` viser resultatet av tokenvalideringen;
`/arkiv` og `/hente` krever et gyldig token for Salesforce-integrasjonen, med unntak av testbypass beskrevet nedenfor.

## Konfigurasjon

| Variabel | Betydning                                                                                                                                                                                                                                                                                                                                                                                             |
|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DEV_TEST_BYPASS_ALLOWED` | `"true"` tillater testkall uten token i `dev-gcp` når `kilde` er `"test"` (første oppføring i `/arkiv`, søkefeltet i `/hente`). Manglende eller annen verdi slår av bypass. Bruk for testing direkte i swagger.                                                                                                                                                                                       |
| `HENTE_ALLOWED` | `"true"` godkjenner kall til `/hente`. `"false"`, manglende eller annen verdi gir 403, også med gyldig token eller testbypass. Påvirker ikke `/arkiv`.                                                                                                                                                                                                                                                |
| `DB_INIT_ALLOWED` | `"true"` godkjenner kall til `/initDb`. Brukes for å opprette databasen hvis den ikke allerede finnes i clusteret. Åpne [![init (dev)](https://img.shields.io/badge/DEV-init-2ea043)](https://sf-arkiv-template.intern.dev.nav.no/internal/initDb) eller [![init (prod)](https://img.shields.io/badge/PROD-init-blue)](https://sf-arkiv-template.intern.nav.no/internal/initDb) for å opprette databasen. |
| `DUMMY_READ_TO_TEST_READINESS` | `"true"` gjør at appen kjører et hente-kall for data med `kilde` `"test"` før podden markeres som ready for Kubernetes. Sikrer at databasetilkoblingen er etablert.                                                                                                                                                                                                                                   |

Endepunkter som viser hvilken konfigurasjon som er aktiv:

[![activeConfig (dev)](https://img.shields.io/badge/DEV-activeConfig-2ea043)](https://sf-arkiv-template.intern.dev.nav.no/internal/activeConfig)
[![activeConfig (prod)](https://img.shields.io/badge/PROD-activeConfig-blue)](https://sf-arkiv-template.intern.nav.no/internal/activeConfig)

## Diagnostics
En del statistikk kan ses her:

[![dbSize (dev)](https://img.shields.io/badge/DEV-dbSize-2ea043)](https://sf-arkiv-template.intern.dev.nav.no/internal/dbSize) [![dbGrowth (dev)](https://img.shields.io/badge/DEV-dbGrowth-2ea043)](https://sf-arkiv-template.intern.dev.nav.no/internal/dbGrowth)

[![dbSize (prod)](https://img.shields.io/badge/PROD-dbSize-blue)](https://sf-arkiv-template.intern.nav.no/internal/dbSize) [![dbGrowth (prod)](https://img.shields.io/badge/PROD-dbGrowth-blue)](https://sf-arkiv-template.intern.nav.no/internal/dbGrowth)

## Metrikker

Appen eksponerer disse tellerne til Prometheus:

[![Prometheus (dev)](https://img.shields.io/badge/DEV-Prometheus-2ea043?logo=prometheus&logoColor=white)](https://sf-arkiv-template.intern.dev.nav.no/internal/prometheus)
[![Prometheus (prod)](https://img.shields.io/badge/PROD-Prometheus-blue?logo=prometheus&logoColor=white)](https://sf-arkiv-template.intern.nav.no/internal/prometheus)

| Metrikk | Betydning |
|---|---|
| `request_arkiv` | Antall kall til `POST /arkiv`, uavhengig av om de godkjennes. |
| `request_hente` | Antall kall til `POST /hente`, også når henting er slått av. |
| `inserted_entries` | Antall arkivposter som er lagret i databasen. |
| `issues` | Antall feil: ugyldig JSON til `/arkiv`, midlertidig databasefeil og uventede feil. Ugyldig JSON til `/hente` teller også. |
| `arkiv_item` | Antall lagrede arkivposter, fordelt på labels: `opprettet_av`, `kilde`, `tema` og `konfidentiellt`. |
