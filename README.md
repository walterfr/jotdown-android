# Jotdown — Fichador Acadêmico para Android

> Leitor de PDF com anotações, fichamentos e exportação ABNT — 100% offline, sem conta, sem rastreamento.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![F-Droid](https://img.shields.io/badge/F--Droid-pending-lightgrey.svg)](https://gitlab.com/fdroid/rfp/-/work_items/3833)

---

## Funcionalidades

- **Leitor de PDF** com renderização lazy multi-página via `PdfRenderer`
- **Camada de anotações** — caneta, lápis, marca-texto e borracha com suporte a pressão de caneta stylus
- **Seletor de espessura** — três tamanhos pré-definidos por ferramenta (fino, médio, grosso)
- **Balões de post-it** — notas ancoradas a coordenadas do PDF, armazenadas em Room
- **Marcações e fichamentos** — captura de trechos com OCR opcional
- **Exportação ABNT** — metadados acadêmicos com saída `.MD`, `.TXT` e `.PDF`
- **Pastas e rótulos** — organização da biblioteca com drag-and-drop
- **Backup/restauração** — exportação em `.zip` de toda a biblioteca
- **Sem rede** — nenhuma permissão de internet, nenhuma conta, nenhuma telemetria

---

## Build Variants

O projeto possui duas _product flavors_ com propósitos distintos:

| Flavor | ID de build | OCR | Dependências proprietárias | Indicado para |
|---|---|---|---|---|
| **`foss`** | `br.com.jotdown` | Manual (digitação) | ❌ Nenhuma | **F-Droid**, sideload |
| **`full`** | `br.com.jotdown` | Automático via ML Kit | ✅ `com.google.mlkit:text-recognition` | Distribuição alternativa |

### Como compilar

```bash
# Flavor FOSS (sem dependências proprietárias) — recomendado para F-Droid
./gradlew assembleFossRelease

# Flavor completo (com OCR automático via Google ML Kit)
./gradlew assembleFullRelease
```

> **Para o F-Droid use sempre `fossRelease`.**  
> O sufixo `-FOSS` é adicionado automaticamente ao `versionName` pelo `productFlavor`.

---

## Arquitetura

```
br.com.jotdown/
├── data/
│   ├── dao/          # DAOs do Room (DocumentDao, AnnotationDao, DrawingDao…)
│   ├── entity/       # Entidades persistidas
│   └── repository/   # DocumentRepository — única fonte de verdade
├── ui/
│   ├── screens/
│   │   ├── library/  # LibraryScreen — grade de documentos com drag-and-drop
│   │   ├── reader/   # ReaderScreen, DrawingLayer, ReaderToolsBar, ReaderTopBar
│   │   └── splash/   # SplashScreen
│   ├── theme/        # MaterialTheme, paleta de cores
│   └── viewmodel/    # LibraryViewModel, ReaderViewModel
└── JotdownApplication.kt
```

**Stack:**
- Jetpack Compose + Material 3
- Room (SQLite) para persistência
- `android.graphics.pdf.PdfRenderer` para renderização
- Navigation Compose com transições `slideInHorizontally`
- Kotlin Coroutines + StateFlow

---

## Requisitos

| Item | Valor |
|---|---|
| `minSdk` | 26 (Android 8.0 Oreo) |
| `targetSdk` | 34 (Android 14) |
| `compileSdk` | 34 |
| JVM target | 17 |

---

## Anti-features

| Anti-feature | Flavor `foss` | Flavor `full` |
|---|---|---|
| Proprietary deps | ❌ | ✅ ML Kit OCR |
| Network | ❌ | ❌ |
| Ads / Tracking | ❌ | ❌ |
| Account required | ❌ | ❌ |

---

## Licença

[MIT](LICENSE) © Walter Rebouças
