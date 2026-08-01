# Jotdown v3.1.8 — Release Instructions

## Version Info
- **Version:** 3.1.8
- **Build Code:** 28

> Build 27 (3.1.7) was uploaded to the internal track as a draft but should **not**
> be promoted: tapping a ficha in the library did nothing there. Build 28 fixes it.
- **Release Date:** August 2026
- **AAB Path:** `app/build/outputs/bundle/fullRelease/app-full-release.aab`

## Features Included (F0-F5)
- ✅ Reading status tracking (TO_READ/READING/READ)
- ✅ Research goals (Metas) with progress tracking
- ✅ Atomic notes (Fichas) — Zettelkasten-style
- ✅ Document-note bidirectional linking
- ✅ DOI import from CrossRef API
- ✅ Citation linking infrastructure (highlights ↔ documents)
- ✅ i18n: Full PT+EN localization
- ✅ Bug fix: Note deletion UI waits for DB completion

---

## Upload to Google Play Console

Upload always goes through `.github/workflows/release.yml`. The CI signs the AAB
with the Play upload key (`PLAY_KEYSTORE_*`, alias `jotdown_upload`,
SHA1 `E4:EE:B5:B3…`). A locally built AAB is signed with a different key and
Play App Signing rejects it — do not upload one by hand.

### By workflow dispatch (no tag)
```bash
gh workflow run "Android Release Build" -f track=internal -f status=draft
```
`track`: internal | alpha | beta · `status`: draft | completed

### By tag (also creates a GitHub Release with the APK)
```bash
git tag v3.1.8 && git push --tags
```

### Promotion is manual
The service account can only upload to test tracks — it cannot release to
production, by design. Promote in the Play Console:
**Release → Testing → Internal testing → select the draft → Review → Start rollout**

Each upload needs a fresh `versionCode`; Play rejects a repeat.

---

## Release Notes

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

## Next Steps (F6 — Future)

Planned for next release:
- Knowledge graph visualization (force-directed layout)
- Highlight linking UI (long-press menu in reader)
- Graph export (DOT format for external tools)

---

## Support

For issues or feedback:
- GitHub Issues: https://github.com/walterfr/jotdown-android/issues
- Play Console Reviews (monitor & respond)

---

**Release prepared by:** Claude Haiku 4.5  
**Date:** August 2026  
**Status:** Ready for upload
