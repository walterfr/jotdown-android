# Jotdown — Release Instructions

## Version Info
- **Current version:** 3.2.0
- **Build Code:** 44
- **Last release:** November 2026 (GitHub Releases & Play Console internal track as `completed`)
- **AAB Path:** `app/build/outputs/bundle/fullRelease/app-full-release.aab`
- **APKs Path:** `app/build/outputs/apk/{full,foss}/release/app-*.apk`

> Bump `versionCode`/`versionName` in `app/build.gradle` before every upload.

## Versioning Scheme (Pride Versioning 🏳️‍🌈 0.3.0)
From v3.1.23 onwards, the project adopts [Pride Versioning](https://pridever.org/) (`PROUD.DEFAULT.SHAME`):
- **PROUD (Major):** Bump when releasing milestone features you are really proud of (can reset the following digits, e.g. `4.0.0`).
- **DEFAULT (Minor):** Bump when making normal, expected, routine feature releases.
- **SHAME (Patch):** Bump when making hotfixes, emergency bug fixes, or corrections too embarrassing to admit.

## Recent Features (3.2.0)
- ✅ Formatos de citação adicionais: APA 7ª Edição e BibTeX (`.bib` export)
- ✅ Busca e importação de metadados por ISBN via Open Library Books API
- ✅ Busca textual completa em background no conteúdo do PDF (`PDFTextStripper`)
- ✅ Exportação de PDF com anotações e traços mesclados (Flatten PDF)
- ✅ Navegação por sumário hierárquico do PDF (Bookmarks / Outlines)

---

## Release Process

### 1. Via GitHub Actions (Recommended)
Building via CI guarantees deterministic/reproducible builds matching F-Droid's Linux environment.

**By tag (Publishes GitHub Release with APKs and uploads AAB to Play Console):**
```bash
git tag v3.1.24 && git push --tags
```

**By workflow dispatch (without creating tag or for draft/test promotion):**
```bash
gh workflow run "Android Release Build" -f track=internal -f status=completed
```
`track`: internal | alpha | beta · `status`: completed | draft

**Rebuild / Repackage tag assets:**
Workflow dispatch allows rebuilding clean APKs for an existing tag if needed for F-Droid reproducibility checks.

### 2. Local Build (Fallback)
If CI quota is exhausted or runners are unavailable, release artifacts can be built locally:
- APKs (FOSS & Full) use `jotdown-keystore.jks` (alias `key0`).
- AAB (Play Console) uses `play-upload-keystore.p12` (alias `jotdown_upload`).
- Run `./gradlew assembleFullRelease assembleFossRelease bundleFullRelease`.
- Push release to GitHub using `gh release create` and upload AAB via Google Play API script.

---

## Release Notes (3.1.8 — F0-F5 roadmap, kept for reference)

### English (en-US)
```
F0-F5 Feature Roadmap Complete!

✨ New Features:
• Reading Status Tracking: Mark documents as To Read, Reading, or Read
• Research Goals (Metas): Create folders with progress tracking and deadlines
• Atomic Notes (Fichas): Zettelkasten-style note-taking linked to document pages
• Document-Note Linking: Create notes directly from PDF pages with source tracking
• DOI Import: Auto-fill metadata from CrossRef API by entering a DOI
• Citation Infrastructure: Link highlights to source documents (foundation for knowledge graph)

🐛 Bug Fixes:
• Note deletion now waits for DB completion before closing editor (badge updates correctly)

🌍 Localization:
• Full Portuguese (PT-BR) and English (US) support
• All UI strings localized

📚 Documentation:
• New user manual: MANUAL_PT.md with complete usage guide for all features
• All features tested on Android 11+ devices

⚡ Performance:
• Offline-first architecture (zero internet required for core features)
• Autosave for notes (saves on every keystroke)
• Optimized database queries with proper indexing
```

### Portuguese (pt-BR)
```
Roadmap F0-F5 Completo! 🎉

✨ Novas Funcionalidades:
• Rastreamento de Status de Leitura: Marque documentos como Para Ler, Lendo ou Lido
• Metas de Pesquisa: Crie pastas com progresso circular e prazos
• Fichas Atômicas: Notas tipo Zettelkasten vinculadas às páginas do PDF
• Vínculo Documento-Ficha: Crie fichas direto do leitor com rastreamento de origem
• Importar DOI: Preencha metadados automaticamente via API CrossRef
• Infraestrutura de Citações: Vincule destacados a documentos fonte (base para grafo)

🐛 Correções:
• Deleção de ficha aguarda conclusão DB antes de fechar (badge atualiza corretamente)

🌍 Localização:
• Suporte completo em Português (BR) e Inglês (US)
• Todas strings da UI localizadas

📚 Documentação:
• Novo manual de usuário: MANUAL_PT.md com guia completo de uso
• Todas funcionalidades testadas em Android 11+

⚡ Performance:
• Arquitetura offline-first (zero internet necessária para funcionalidades core)
• Autosalva em fichas (salva a cada digitação)
• Queries otimizadas com índices apropriados
```

---

## Screenshots & Assets

Update these on Play Console:
- App icon: 512x512 (already up to date)
- Feature graphic: 1024x500 (update with new features)
- Screenshots: Update to show:
  1. Library with reading status filters
  2. Research goal with progress indicator
  3. PDF reader with note creation button
  4. Note editor with source document indicator
  5. Metadata sheet with DOI search field

---

## Testing Checklist

Before release:
- [ ] App opens without crashes (internal/external storage)
- [ ] Library loads all documents
- [ ] Reading status filters work (Para Ler/Lendo/Lido)
- [ ] Can create and view metas with progress
- [ ] Can create fichas and see count badge update
- [ ] Deleting ficha updates badge (bug fix)
- [ ] DOI import works (requires internet)
- [ ] Metadata saves correctly
- [ ] App languages switch (PT/EN)
- [ ] Offline functionality verified (no internet)

---

## Rollout Strategy

1. **Internal Track** (48h): Test with internal testers
2. **Staging Track** (optional): QA final pass
3. **Production Track**: Full rollout
   - Consider: 50% for 1 day → 100% if no critical issues

---

## Monitoring Post-Release

Watch for:
- Crash reports in Play Console
- User reviews mentioning bugs
- ANR (Application Not Responding) errors
- Storage permission issues on Android 13+

Check logs:
- Firebase Crashlytics (if configured)
- Play Console → Vitals → Crashes & ANRs

---

## Support

For issues or feedback:
- GitHub Issues: https://github.com/walterfr/jotdown-android/issues
- Play Console Reviews (monitor & respond)
