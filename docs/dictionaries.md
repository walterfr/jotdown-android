# Offline translation dictionaries

Jotdown expects downloadable SQLite dictionaries in the GitHub release
`v1.0-dictionaries` using these names:

- `dict_en.db`: English to Portuguese
- `dict_pt.db`: Portuguese to English

Recommended source data:

- FreeDict English-Portuguese 0.3:
  `https://download.freedict.org/dictionaries/eng-por/0.3/freedict-eng-por-0.3.stardict.tar.xz`
- FreeDict Portuguese-English 0.1.1:
  `https://download.freedict.org/dictionaries/por-eng/0.1.1/freedict-por-eng-0.1.1.dictd.tar.bz2`

Build command:

```bash
python scripts/build_freedict_sqlite.py \
  --eng-por freedict-eng-por-0.3.stardict.tar.xz \
  --por-eng freedict-por-eng-0.1.1.dictd.tar.bz2 \
  --out release-dictionaries
```

Upload the generated files to the release:

- `release-dictionaries/dict_en.db`
- `release-dictionaries/dict_pt.db`
- `release-dictionaries/DICTIONARY_NOTICE.txt`

The app downloads them through `OfflineDictionaryManager` as `dict_$language.db`,
so no app code change is needed when these files are attached to the existing
release.
