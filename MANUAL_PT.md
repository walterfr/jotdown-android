# Jotdown v3.1.6 — Manual de Utilização

## Visão Geral

Jotdown é um leitor de PDF e gerenciador de notas acadêmicas. Permite ler PDFs, tomar notas atômicas (fichas), organizar em metas de pesquisa e rastrear o progresso de leitura.

---

## 1. Biblioteca e Status de Leitura

### Abrir Biblioteca
Ao abrir o Jotdown, você vê sua biblioteca de documentos com três seções:
- **Todos**: todos os documentos
- **Recentes**: últimos acessados
- **Favoritos**: marcados como favoritos

### Filtrar por Status de Leitura
Abra o **Menu Lateral** e selecione:
- **Para Ler** (bookmark aberto): documentos não iniciados
- **Lendo** (livro aberto): em progresso
- **Lido** (check): finalizados

**Badge nos cards:** cor **âmbar** = Lendo | cor **verde** = Lido | nenhuma cor = Para Ler

---

## 2. Metas de Pesquisa

### Criar Uma Meta
1. Na biblioteca, clique botão **"+ Nova Meta"** (FAB azul)
2. Digite nome da meta e descrição (opcional)
3. Configure prazo (opcional)
4. Clique **"Criar"**

### Rastrear Progresso
- Metas aparecem como **cards com progresso circular**
- Progresso = documentos **Lido** / total de documentos na pasta meta
- Se prazo venceu, aparece **data em vermelho**

### Ver Apenas Metas
- Menu → **Metas** (aba dedicada)

---

## 3. Ler PDF e Criar Fichas

### Abrir PDF
1. Clique num documento na biblioteca
2. PDF abre no leitor

### Recursos do Leitor
- **🔍 Busca**: ícone lupa → buscar texto no PDF
- **📖 Metadados**: ícone livro → ver/editar metadados (autor, título, publisher)
- **🌙 Modo noturno**: ícone lua → alternar tema
- **⛶ Tela cheia**: ícone → modo tela cheia
- **📝 Anotações**: ícone message → ver anotações
- **Fichas**: ícone sticky note com badge → criar ficha desta página

### Criar Ficha (Nota Atômica)
1. No leitor, clique ícone **Fichas** (StickyNote2, mostra contagem)
2. Clique **"Nova Ficha"**
3. Digite título e conteúdo
4. **Autosalva** automaticamente
5. Ao abrir a ficha, mostra **"Source: Page X"** indicando de qual página veio

### Visualizar Todas as Fichas
- Biblioteca → abrir aba **Fichas** no final

---

## 4. Importar Metadados via DOI

### O que é DOI
DOI (Identificador de Objeto Digital) é um código único que identifica artigos acadêmicos. Ex: `10.1038/nature12373`

### Importar Metadados
1. Abra PDF no leitor
2. Clique ícone **Metadados** (📖 livro)
3. Vá até seção **DOI** (última seção)
4. Digite DOI no campo (ex: `10.1038/nature12373`)
5. Clique botão **"Buscar"**
6. **Aguarde**: API CrossRef busca os dados (~2-3 segundos)
7. Campos preenchem automaticamente:
   - **Título**
   - **Autor(es)**
   - **Publisher**
   - **Ano**
   - **Journal/Periódico**
   - **Volume/Páginas**
8. Clique **"Salvar"** na base do sheet

---

## 5. Editar Metadados Manualmente

1. Leitor → ícone **Metadados** (📖)
2. Escolha **tipo de publicação**: Livro, Artigo, Capítulo, Tese, Documento Jurídico
3. Preencha campos conforme tipo
4. Adicione **URL** (link do artigo/publisher)
5. Digite **data de acesso** (quando você consultou)
6. Veja **preview ABNT** na base
7. **Exportar** em PDF, TXT ou Markdown (botões coloridos na base)
8. Clique **"Salvar"**

---

## 6. Editar e Deletar Fichas

### Editar Ficha
1. Biblioteca → aba **Fichas**
2. Clique na ficha
3. Edite título e conteúdo
4. **Autosalva**
5. Clique **← Voltar** pra sair

### Deletar Ficha
1. No editor de ficha, clique ícone **lixeira** (🗑️ vermelho, topo direito)
2. Confirme **"Deletar"**
3. Ficha removida, volta à biblioteca
4. **Badge de contagem atualiza**

---

## 7. Vincular Citações a Documentos

Quando um trecho destacado cita outra obra, você pode registrar esse vínculo.

### Criar Vínculo
1. No leitor, abra **Metadados** (ícone 📖)
2. Role até a seção de **citações** (aparece quando há destaques)
3. Em cada citação há um ícone de **corrente (link)**, ao lado do excluir
4. Clique no link → abre lista de documentos da sua biblioteca
5. Busque e selecione o documento citado

### Ler o Estado
- Ícone de link **azul** = citação já vinculada
- Ícone de link **cinza** = sem vínculo

### Remover Vínculo
- Abra o seletor e clique **"Remover Vínculo"** na base

**Nota:** o documento aberto não aparece na lista — uma citação não se vincula à própria obra.

### Em desenvolvimento (F6)
- Visualização de grafo de conhecimento (fichas ↔ documentos ↔ citações)

---

## 8. Atalhos e Dicas

- **Seleção de texto em PDF**: selecione e aparecerá opção de destacar ou criar anotação
- **Modo offline**: tudo funciona 100% offline, nenhuma internet necessária
- **Backup automático**: dados sincronizados com Google Drive (se conectado em Settings)
- **Localização**: app em PT-BR e EN, mude em Settings → Idioma

---

## 9. Troubleshooting

| Problema | Solução |
|----------|---------|
| Fichas não aparecem no badge | Atualize: feche e reabra o leitor |
| DOI não encontra resultado | Verifique formato (`10.xxxx/xxxxx`), conexão internet |
| Metadados não salvam | Clique **"Salvar"** (botão azul na base do sheet) |
| PDF não abre | Arquivo corrompido ou formato não suportado |

---

## Versão
**Jotdown v3.1.6** — Build 26  
Última atualização: Agosto 2026
